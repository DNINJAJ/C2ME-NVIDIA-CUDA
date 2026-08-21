package com.ishland.c2me.opts.accel.opencl.common.gen;

import com.ishland.c2me.base.common.scheduler.IVanillaChunkManager;
import com.ishland.c2me.base.mixin.access.IAquiferSamplerImpl;
import com.ishland.c2me.base.mixin.access.IChunkSection;
import com.ishland.c2me.opts.accel.opencl.common.Config;
import com.ishland.c2me.opts.accel.opencl.common.compiler.GeneratedCLSource;
import com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen;
import com.ishland.c2me.opts.accel.opencl.common.compiler.emitters.misc.CLBlockStateMappings;
import com.ishland.c2me.opts.accel.opencl.common.ducks.PalettedContainerExtension;
import com.ishland.c2me.opts.accel.opencl.common.gen.cache.Stage1Cache;
import com.ishland.c2me.opts.accel.opencl.common.integration.zfastnoise.ZFastNoiseBindings;
import com.ishland.c2me.opts.accel.opencl.common.util.TLUtil;
import com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Batched world generation backed exclusively by CUDA. The original class
 * name and API are retained because the existing C2ME mixins reference them.
 */
public final class CLServerBatchedBiomeNoiseContext {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    public static final int BATCH_SIZE = Config.useSmallerBatches ? 2 : 4;
    public static final int BATCH_MASK = BATCH_SIZE - 1;
    public static final int BATCH_SHIFT = Integer.bitCount(BATCH_MASK);

    public static boolean isAligned(int x, int z) {
        return (x & BATCH_MASK) == 0 && (z & BATCH_MASK) == 0;
    }

    private final ChunkPos startingPos;
    private final CLServerWorldContext worldContext;
    private final NoiseChunkGenerator generator;
    private final NoiseConfig noiseConfig;

    public CLServerBatchedBiomeNoiseContext(ChunkPos startingPos, CLServerWorldContext worldContext,
                                             NoiseChunkGenerator generator, NoiseConfig noiseConfig) {
        this.startingPos = Objects.requireNonNull(startingPos);
        this.worldContext = Objects.requireNonNull(worldContext);
        this.generator = Objects.requireNonNull(generator);
        this.noiseConfig = Objects.requireNonNull(noiseConfig);
    }

    public CompletableFuture<Void> execute(ChunkLoadingContext context,
                                            BoundedRegionArray<ProtoChunk> chunks,
                                            BoundedRegionArray<StructureAccessor> structureAccessors) {
        return this.worldContext.getEstimateSurfaceHeightCache()
                .getAreaCache(startingPos.x(), startingPos.z(), BATCH_SIZE, BATCH_SIZE)
                .thenComposeAsync(cacheEntry -> {
                    ByteBuffer rwData = ScopedValue.where(TLUtil.stage1CachePassing, cacheEntry).call(() ->
                            CLDataUtil.worldgen_data_root$createForArea(
                                    this.startingPos, BATCH_SIZE, chunks, this.generator, this.noiseConfig,
                                    structureAccessors, this.worldContext.getClBlockStateMappings(),
                                    this.worldContext.getGeneratedCLSource(), cacheEntry));
                    return this.worldContext.borrowCommandQueue().thenCompose(pair ->
                            CompletableFuture.runAsync(
                                    () -> executeCuda(chunks, structureAccessors, rwData, pair.left(), pair.right()),
                                    pair.left().getDevice().getExecutor())
                                    .whenComplete((ignored, throwable) -> {
                                        if (rwData != null) MemoryUtil.memFree(rwData);
                                        pair.left().close();
                                    }));
                }, ((IVanillaChunkManager) context.tacs()).c2me$getSchedulingManager()
                        .positionedExecutor(this.startingPos.toLong()));
    }

    private void executeCuda(BoundedRegionArray<ProtoChunk> chunks,
                             BoundedRegionArray<StructureAccessor> structureAccessors,
                             ByteBuffer rwData,
                             OpenCLDevice.BorrowedCommandQueue commandQueue,
                             CLServerWorldContext.DeviceWithProgram deviceWithProgram) {
        OpenCLDevice device = deviceWithProgram.device();
        ChunkGeneratorSettings settings = this.generator.getSettings().value();
        int verticalCellBlockCount = settings.generationShapeConfig().verticalCellBlockCount();
        int horizontalCellBlockCount = settings.generationShapeConfig().horizontalCellBlockCount();
        int horizontalCellsCount = Math.floorDiv(16, horizontalCellBlockCount) * BATCH_SIZE;
        int verticalCellsCount = Math.floorDiv(settings.generationShapeConfig().height(), verticalCellBlockCount);
        int horizontalSize = 16 * BATCH_SIZE;
        int verticalSize = verticalCellsCount * verticalCellBlockCount;
        ProtoChunk startingChunk = chunks.get(this.startingPos.x(), this.startingPos.z());
        int biomeHeight = (startingChunk.getHeightLimitView().getTopSectionCoord()
                - startingChunk.getHeightLimitView().getBottomSectionCoord() + 1) * 4;

        long rwBuffer = 0L;
        long biomeOutBuffer = 0L;
        long blockOutBuffer = 0L;
        try {
            rwBuffer = device.allocate(rwData.remaining());
            device.copyToDevice(commandQueue.getStream(), rwBuffer, rwData);
            int biomeOutCount = biomeHeight * 4 * BATCH_SIZE * 4 * BATCH_SIZE;
            GeneratedCLSource source = this.worldContext.getGeneratedCLSource();
            if (source.getBiomeMappings() != null) {
                biomeOutBuffer = device.allocate((long) biomeOutCount * Integer.BYTES);
            }
            blockOutBuffer = device.allocate((long) horizontalSize * verticalSize * horizontalSize * Integer.BYTES);
            long constData = deviceWithProgram.programConstDataBuffer();

            if (biomeOutBuffer != 0L) {
                launch(device, commandQueue.getStream(), deviceWithProgram, OpenCLCGen.ProgramType.BIOME_MULTINOISE_KERNEL,
                        "df_biome_multinoise_kernel", ceilDiv(4 * BATCH_SIZE, 8),
                        ceilDiv(4 * BATCH_SIZE, 8), biomeHeight, 8, 8, 1,
                        constData, rwBuffer, biomeOutBuffer,
                        BiomeCoords.fromBlock(this.startingPos.getStartX()),
                        BiomeCoords.fromBlock(this.startingPos.getStartZ()),
                        BiomeCoords.fromBlock(startingChunk.getBottomY()),
                        4 * BATCH_SIZE, 4 * BATCH_SIZE, biomeHeight);
            }
            if (source.getInterpolatorPrefills() != 0) {
                launch(device, commandQueue.getStream(), deviceWithProgram, OpenCLCGen.ProgramType.INTERPOLATOR_PREFILL,
                        "df_interpolator_buffer_prefill_kernel", horizontalCellsCount + 1,
                        horizontalCellsCount + 1, verticalCellsCount + 1, 8, 8, 4,
                        constData, rwBuffer, horizontalCellsCount + 1,
                        horizontalCellsCount + 1, verticalCellsCount + 1);
            }
            if (settings.hasAquifers()) {
                int startX = IAquiferSamplerImpl.invokeGetLocalX(this.startingPos.getStartX() - 5);
                int startY = IAquiferSamplerImpl.invokeGetLocalY(settings.generationShapeConfig().minimumY() + 1) - 1;
                int startZ = IAquiferSamplerImpl.invokeGetLocalZ(this.startingPos.getStartZ() - 5);
                ChunkPos endChunkPos = new ChunkPos(this.startingPos.x() + BATCH_SIZE - 1,
                        this.startingPos.z() + BATCH_SIZE - 1);
                int endX = IAquiferSamplerImpl.invokeGetLocalX(endChunkPos.getEndX() + 4);
                int endY = IAquiferSamplerImpl.invokeGetLocalY(settings.generationShapeConfig().minimumY()
                        + settings.generationShapeConfig().height() - 1) + 1;
                int endZ = IAquiferSamplerImpl.invokeGetLocalZ(endChunkPos.getEndZ() + 4);
                launch(device, commandQueue.getStream(), deviceWithProgram, OpenCLCGen.ProgramType.AQUIFER_PREFILL,
                        "aquifer_data_prefill", endX - startX + 1,
                        endZ - startZ + 1, endY - startY + 1, 8, 8, 4,
                        constData, rwBuffer, endX - startX + 1,
                        endY - startY + 1, endZ - startZ + 1);
            }
            if (source.getCache2dPrefills() != 0) {
                launch(device, commandQueue.getStream(), deviceWithProgram, OpenCLCGen.ProgramType.CACHE2D_PREFILL,
                        "df_cache2d_prefill_kernel", ceilDiv(horizontalSize, 8),
                        ceilDiv(horizontalSize, 8), source.getCache2dPrefills(), 8, 8, 1,
                        constData, rwBuffer);
            }
            launch(device, commandQueue.getStream(), deviceWithProgram, OpenCLCGen.ProgramType.NOISE_KERNEL,
                    "df_noise_kernel", ceilDiv(horizontalSize, 16), ceilDiv(horizontalSize, 16),
                    verticalSize, 16, 16, 1, constData, rwBuffer, blockOutBuffer,
                    this.startingPos.x(), this.startingPos.z());
            if (biomeOutBuffer != 0L) {
                ByteBuffer biomeBytes = ByteBuffer.allocateDirect(biomeOutCount * Integer.BYTES)
                        .order(java.nio.ByteOrder.nativeOrder());
                device.copyFromDevice(commandQueue.getStream(), biomeOutBuffer, biomeBytes);
                device.synchronize(commandQueue.getStream());
                writeBiomes(chunks, source, biomeHeight, biomeBytes.flip().asIntBuffer());
            } else {
                genBiomesFallback(chunks, structureAccessors);
            }
            ByteBuffer blockBytes = ByteBuffer.allocateDirect(horizontalSize * verticalSize * horizontalSize);
            device.copyFromDevice(commandQueue.getStream(), blockOutBuffer, blockBytes);
            device.synchronize(commandQueue.getStream());
            writeBlocks(chunks, this.worldContext.getClBlockStateMappings(), verticalSize, settings,
                    horizontalSize, blockBytes.flip());
        } finally {
            if (rwBuffer != 0L) device.free(rwBuffer);
            if (biomeOutBuffer != 0L) device.free(biomeOutBuffer);
            if (blockOutBuffer != 0L) device.free(blockOutBuffer);
        }
    }

    private static void launch(OpenCLDevice device, long stream, CLServerWorldContext.DeviceWithProgram program,
                               OpenCLCGen.ProgramType type, String kernelName,
                               int gridX, int gridY, int gridZ, int blockX, int blockY, int blockZ,
                               long... args) {
        device.launch(stream, device.getKernel(program.getProgram(type), kernelName), gridX, gridY, gridZ,
                blockX, blockY, blockZ, args);
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private void genBiomesFallback(BoundedRegionArray<ProtoChunk> chunks,
                                   BoundedRegionArray<StructureAccessor> structureAccessors) {
        for (int chunkOffX = 0; chunkOffX < BATCH_SIZE; chunkOffX++) {
            for (int chunkOffZ = 0; chunkOffZ < BATCH_SIZE; chunkOffZ++) {
                ProtoChunk chunk = chunks.get(this.startingPos.x() + chunkOffX, this.startingPos.z() + chunkOffZ);
                if (chunk.getStatus().isAtLeast(ChunkStatus.BIOMES)) continue;
                this.generator.populateBiomes(this.noiseConfig, Blender.getNoBlending(),
                        structureAccessors.get(chunk.getPos().x(), chunk.getPos().z()), chunk);
                chunk.setStatus(ChunkStatus.BIOMES);
            }
        }
    }

    private void writeBiomes(BoundedRegionArray<ProtoChunk> chunks, GeneratedCLSource source,
                             int biomeHeight, IntBuffer values) {
        RegistryEntry<Biome>[] mappings = source.getBiomeMappings();
        ProtoChunk startingChunk = chunks.get(this.startingPos.x(), this.startingPos.z());
        int currentSection = startingChunk.getSectionIndex(startingChunk.getBottomY());
        ChunkSection[] sections = new ChunkSection[BATCH_SIZE * BATCH_SIZE];
        PalettedContainer<RegistryEntry<Biome>>[] containers = new PalettedContainer[BATCH_SIZE * BATCH_SIZE];
        BitSet protectedChunks = new BitSet(BATCH_SIZE * BATCH_SIZE);

        for (int z = 0; z < BATCH_SIZE; z++) {
            for (int x = 0; x < BATCH_SIZE; x++) {
                int index = (z << BATCH_SHIFT) + x;
                ProtoChunk chunk = chunks.get(this.startingPos.x() + x, this.startingPos.z() + z);
                if (chunk.getStatus().isAtLeast(ChunkStatus.BIOMES)) {
                    protectedChunks.set(index);
                    continue;
                }
                sections[index] = chunk.getSection(currentSection);
                containers[index] = sections[index].getBiomeContainer().slice();
            }
        }

        int horizontalBiomeSize = 4 << BATCH_SHIFT;
        for (int y = 0; y < biomeHeight; y++) {
            int biomeY = BiomeCoords.fromBlock(startingChunk.getBottomY()) + y;
            int sectionIndex = startingChunk.getSectionIndex(BiomeCoords.toBlock(biomeY));
            if (sectionIndex != currentSection) {
                for (int z = 0; z < BATCH_SIZE; z++) {
                    for (int x = 0; x < BATCH_SIZE; x++) {
                        int index = (z << BATCH_SHIFT) + x;
                        if (protectedChunks.get(index)) continue;
                        ((IChunkSection) sections[index]).setBiomeContainer(containers[index]);
                        sections[index] = chunks.get(this.startingPos.x() + x, this.startingPos.z() + z)
                                .getSection(sectionIndex);
                        containers[index] = sections[index].getBiomeContainer().slice();
                    }
                }
                currentSection = sectionIndex;
            }
            for (int z = 0; z < horizontalBiomeSize; z++) {
                for (int x = 0; x < horizontalBiomeSize; x++) {
                    int value = values.get((y * horizontalBiomeSize + z) * horizontalBiomeSize + x);
                    int chunkX = (x >> 2) & BATCH_MASK;
                    int chunkZ = (z >> 2) & BATCH_MASK;
                    int index = (chunkZ << BATCH_SHIFT) + chunkX;
                    if (!protectedChunks.get(index)) {
                        ((PalettedContainerExtension<RegistryEntry<Biome>>) containers[index])
                                .c2me$setUnsafe(x & 3, biomeY & 3, z & 3, mappings[value]);
                    }
                }
            }
        }
        for (int z = 0; z < BATCH_SIZE; z++) {
            for (int x = 0; x < BATCH_SIZE; x++) {
                int index = (z << BATCH_SHIFT) + x;
                if (protectedChunks.get(index)) continue;
                ((IChunkSection) sections[index]).setBiomeContainer(containers[index]);
                chunks.get(this.startingPos.x() + x, this.startingPos.z() + z).setStatus(ChunkStatus.BIOMES);
            }
        }
    }

    private void writeBlocks(BoundedRegionArray<ProtoChunk> chunks, CLBlockStateMappings mappings,
                             int verticalSize, ChunkGeneratorSettings settings, int horizontalSize,
                             ByteBuffer values) {
        if (ZFastNoiseBindings.MH_FastCopyBufferDataIntoChunks$copyData != null) {
            ZFastNoiseBindings.call_FastCopyBufferDataIntoChunks$copyData(chunks, mappings.getIdToBlockState(),
                    verticalSize, settings, horizontalSize, values, this.startingPos, BATCH_SIZE);
            return;
        }
        ProtoChunk startingChunk = chunks.get(this.startingPos.x(), this.startingPos.z());
        int currentSection = startingChunk.getSectionIndex(startingChunk.getBottomY());
        ChunkSection[] sections = new ChunkSection[BATCH_SIZE * BATCH_SIZE];
        int protectedMask = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int z = 0; z < BATCH_SIZE; z++) {
            for (int x = 0; x < BATCH_SIZE; x++) {
                int index = (z << BATCH_SHIFT) + x;
                ProtoChunk chunk = chunks.get(this.startingPos.x() + x, this.startingPos.z() + z);
                if (chunk.getStatus().isAtLeast(ChunkStatus.NOISE)) {
                    protectedMask |= 1 << index;
                    continue;
                }
                sections[index] = chunk.getSection(currentSection);
                sections[index].lock();
            }
        }
        for (int y = 0; y < verticalSize; y++) {
            int blockY = settings.generationShapeConfig().minimumY() + y;
            int sectionIndex = startingChunk.getSectionIndex(blockY);
            if (sectionIndex != currentSection) {
                for (int z = 0; z < BATCH_SIZE; z++) {
                    for (int x = 0; x < BATCH_SIZE; x++) {
                        int index = (z << BATCH_SHIFT) + x;
                        if ((protectedMask & (1 << index)) != 0) continue;
                        sections[index].calculateCounts();
                        sections[index].unlock();
                        sections[index] = chunks.get(this.startingPos.x() + x, this.startingPos.z() + z)
                                .getSection(sectionIndex);
                        sections[index].lock();
                    }
                }
                currentSection = sectionIndex;
            }
            for (int z = 0; z < horizontalSize; z++) {
                for (int x = 0; x < horizontalSize; x++) {
                    int offset = y * horizontalSize * horizontalSize + z * horizontalSize + x;
                    int raw = values.get(offset) & 0xFF;
                    int chunkX = (x >> 4) & BATCH_MASK;
                    int chunkZ = (z >> 4) & BATCH_MASK;
                    int index = (chunkZ << BATCH_SHIFT) + chunkX;
                    if ((protectedMask & (1 << index)) != 0) continue;
                    boolean fluidTick = (raw & 0x80) != 0;
                    int blockId = raw & 0x7F;
                    BlockState state = mappings.getBlockState(blockId);
                    Assertions.assertTrue(state != null);
                    if (state != AIR) {
                        ((PalettedContainerExtension<BlockState>) sections[index].getBlockStateContainer())
                                .c2me$setUnsafe(x & 15, blockY & 15, z & 15, state);
                        if (fluidTick) {
                            ProtoChunk chunk = chunks.get(this.startingPos.x() + chunkX, this.startingPos.z() + chunkZ);
                            mutable.set(x + chunk.getPos().getStartX(), blockY, z + chunk.getPos().getStartZ());
                            chunk.markBlockForPostProcessing(mutable);
                        }
                    }
                }
            }
        }
        for (int z = 0; z < BATCH_SIZE; z++) {
            for (int x = 0; x < BATCH_SIZE; x++) {
                int index = (z << BATCH_SHIFT) + x;
                if ((protectedMask & (1 << index)) != 0) continue;
                sections[index].calculateCounts();
                sections[index].unlock();
                ProtoChunk chunk = chunks.get(this.startingPos.x() + x, this.startingPos.z() + z);
                Heightmap.populateHeightmaps(chunk, EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG,
                        Heightmap.Type.WORLD_SURFACE_WG));
                chunk.setStatus(ChunkStatus.NOISE);
            }
        }
    }
}
