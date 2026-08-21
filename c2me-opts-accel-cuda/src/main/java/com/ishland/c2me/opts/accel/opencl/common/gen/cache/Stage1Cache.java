/*
 * All Rights Reserved
 *
 * Copyright (c) 2025-2026 ishland
 *
 * All rights reserved. Do not redistribute.
 */

package com.ishland.c2me.opts.accel.opencl.common.gen.cache;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ishland.c2me.base.common.GlobalExecutors;
import com.ishland.c2me.opts.accel.opencl.common.Config;
import com.ishland.c2me.opts.accel.opencl.common.gen.CLDataUtil;
import com.ishland.c2me.opts.accel.opencl.common.gen.CLServerWorldContext;
import com.ishland.c2me.opts.accel.opencl.common.gen.OpenCLDevice;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/** CUDA version of the stage-1 cache pipeline. Operations are ordered by the CUDA context. */
public class Stage1Cache {
    private static final int CACHE_CHUNK_WIDTH = Config.useSmallerBatches ? 8 : 16;
    private static final int CACHE_CHUNK_WIDTH_SHIFT = Integer.numberOfTrailingZeros(CACHE_CHUNK_WIDTH);
    private static final int CACHE_WIDTH = CACHE_CHUNK_WIDTH << 2;
    private static final int CACHE_WIDTH_SHIFT = Integer.numberOfTrailingZeros(CACHE_WIDTH);
    private final AsyncLoadingCache<CacheIndex, RawCacheEntry> cache;
    private final CLServerWorldContext worldContext;

    public Stage1Cache(CLServerWorldContext worldContext) {
        this.worldContext = Objects.requireNonNull(worldContext);
        this.cache = Caffeine.newBuilder().maximumSize(256).executor(GlobalExecutors.asyncScheduler).buildAsync(this::asyncLoad0);
    }

    private CompletableFuture<RawCacheEntry> asyncLoad0(CacheIndex index, Executor ignored) {
        return worldContext.borrowCommandQueue().thenCompose(pair ->
                CompletableFuture.supplyAsync(() -> execute0(index, pair), pair.left().getDevice().getExecutor())
                        .thenCompose(Function.identity()));
    }

    private @NotNull CompletableFuture<RawCacheEntry> execute0(CacheIndex index, Pair<OpenCLDevice.BorrowedCommandQueue, CLServerWorldContext.DeviceWithProgram> pair) {
        OpenCLDevice device = pair.left().getDevice();
        CLBufferCache.BufferEntry surface = null, rw = null, flat = null;
        ByteBuffer rwData = null, surfaceData = null, flatData = null;
        try {
            int surfaceBytes = CACHE_WIDTH * CACHE_WIDTH * Integer.BYTES;
            surface = device.getBufferCache().allocate(CLBufferCache.Type.ESTIMATE_SURFACE_HEIGHT_RX, surfaceBytes, device::allocate);
            rwData = CLDataUtil.worldgen_data_root$createForFlatCacheOnly(new ChunkPos(index.x << CACHE_CHUNK_WIDTH_SHIFT, index.z << CACHE_CHUNK_WIDTH_SHIFT), CACHE_CHUNK_WIDTH, worldContext.getGeneratedCLSource(), null, false);
            rw = device.getBufferCache().allocate(CLBufferCache.Type.GEN_STAGE1_RW_DATA, rwData.remaining(), device::allocate);
            device.copyToDevice(pair.left().getStream(), rw.buffer(), rwData);

            int flatPrefills = worldContext.getGeneratedCLSource().getFlatCachePrefills();
            if (flatPrefills > 0) {
                int flatBytes = flatPrefills * CACHE_WIDTH * CACHE_WIDTH * Double.BYTES;
                flat = device.getBufferCache().allocate(CLBufferCache.Type.FLATCACHE_RX, flatBytes, device::allocate);
                for (int i = 0; i < flatPrefills; i++) {
                    long kernel = device.getKernel(pair.right().getProgram(com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen.ProgramType.FLAT_CACHE_PREFILL), "df_flatcache_prefill_kernel_" + i);
                    device.launch(pair.left().getStream(), kernel, (CACHE_WIDTH + 15) / 16, (CACHE_WIDTH + 15) / 16, 1, 16, 16, 1, pair.right().programConstDataBuffer(), rw.buffer(), flat.buffer());
                }
            }
            long kernel = device.getKernel(pair.right().getProgram(com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen.ProgramType.ESTIMATE_SURFACE_HEIGHT), "chunkNoiseSampler_estimateSurfaceHeight_prefill_indep");
            device.launch(pair.left().getStream(), kernel, (CACHE_WIDTH + 7) / 8, (CACHE_WIDTH + 7) / 8, 1, 8, 8, 1, pair.right().programConstDataBuffer(), rw.buffer(), surface.buffer(), index.x << CACHE_CHUNK_WIDTH_SHIFT, index.z << CACHE_CHUNK_WIDTH_SHIFT, CACHE_WIDTH);
            surfaceData = ByteBuffer.allocateDirect(surfaceBytes).order(ByteOrder.nativeOrder());
            device.copyFromDevice(pair.left().getStream(), surface.buffer(), surfaceData);
            int[] surfaceHeights = new int[CACHE_WIDTH * CACHE_WIDTH];
            double[] flatCaches = new double[flatPrefills * CACHE_WIDTH * CACHE_WIDTH];
            if (flat != null) {
                flatData = ByteBuffer.allocateDirect(flatCaches.length * Double.BYTES).order(ByteOrder.nativeOrder());
                device.copyFromDevice(pair.left().getStream(), flat.buffer(), flatData);
            }
            device.synchronize(pair.left().getStream());
            surfaceData.flip().asIntBuffer().get(surfaceHeights);
            if (flatData != null) flatData.flip().asDoubleBuffer().get(flatCaches);
            return CompletableFuture.completedFuture(new RawCacheEntry(surfaceHeights, flatCaches));
        } finally {
            if (surface != null) device.getBufferCache().returnBuffer(CLBufferCache.Type.ESTIMATE_SURFACE_HEIGHT_RX, surface);
            if (rw != null) device.getBufferCache().returnBuffer(CLBufferCache.Type.GEN_STAGE1_RW_DATA, rw);
            if (flat != null) device.getBufferCache().returnBuffer(CLBufferCache.Type.FLATCACHE_RX, flat);
            if (rwData != null) rwData.clear();
            pair.left().close();
        }
    }

    public CompletableFuture<AreaCacheEntry> getChunkCache(int chunkX, int chunkZ) {
        return getAreaCache0(chunkX, chunkZ, 1, 1).thenApply(e -> new AreaCacheEntry(chunkX, chunkZ, 1, 1, e.surfaceHeights, e.flatCaches));
    }

    public CompletableFuture<AreaCacheEntry> getAreaCache(int x, int z, int sx, int sz) {
        return getAreaCache0(x, z, sx, sz).thenApply(e -> new AreaCacheEntry(x, z, sx, sz, e.surfaceHeights, e.flatCaches));
    }

    private CompletableFuture<RawCacheEntry> getAreaCache0(int x, int z, int sx, int sz) {
        int startCacheX = (x - 4) >> CACHE_CHUNK_WIDTH_SHIFT;
        int startCacheZ = (z - 4) >> CACHE_CHUNK_WIDTH_SHIFT;
        int endCacheX = (x + sx - 1 + 4) >> CACHE_CHUNK_WIDTH_SHIFT;
        int endCacheZ = (z + sz - 1 + 4) >> CACHE_CHUNK_WIDTH_SHIFT;

        Map<CacheIndex, CompletableFuture<RawCacheEntry>> futures = new HashMap<>();
        for (int cacheX = startCacheX; cacheX <= endCacheX; cacheX++) {
            for (int cacheZ = startCacheZ; cacheZ <= endCacheZ; cacheZ++) {
                CacheIndex cacheIndex = new CacheIndex(cacheX, cacheZ);
                futures.put(cacheIndex, cache.get(cacheIndex));
            }
        }

        return CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            int surfaceSizeX = 36 + 4 * (sx - 1);
            int surfaceSizeZ = 36 + 4 * (sz - 1);
            int[] surfaceHeights = new int[surfaceSizeX * surfaceSizeZ];
            for (int relX = 0; relX < surfaceSizeX; relX++) {
                for (int relZ = 0; relZ < surfaceSizeZ; relZ++) {
                    int cacheX = (((x - 4) << 2) + relX) >> CACHE_WIDTH_SHIFT;
                    int cacheZ = (((z - 4) << 2) + relZ) >> CACHE_WIDTH_SHIFT;
                    int cacheRelX = (((x - 4) << 2) + relX) & (CACHE_WIDTH - 1);
                    int cacheRelZ = (((z - 4) << 2) + relZ) & (CACHE_WIDTH - 1);
                    int[] cacheData = futures.get(new CacheIndex(cacheX, cacheZ)).join().surfaceHeights();
                    surfaceHeights[relX * surfaceSizeZ + relZ] = cacheData[(cacheRelX << CACHE_WIDTH_SHIFT) + cacheRelZ];
                }
            }

            int flatPrefills = worldContext.getGeneratedCLSource().getFlatCachePrefills();
            int flatSizeX = 5 + 4 * (sx - 1);
            int flatSizeZ = 5 + 4 * (sz - 1);
            int flatArea = flatSizeX * flatSizeZ;
            double[] flatCaches = new double[flatPrefills * flatArea];
            for (int cacheIndex = 0; cacheIndex < flatPrefills; cacheIndex++) {
                for (int relX = 0; relX < flatSizeX; relX++) {
                    for (int relZ = 0; relZ < flatSizeZ; relZ++) {
                        int cacheX = ((x << 2) + relX) >> CACHE_WIDTH_SHIFT;
                        int cacheZ = ((z << 2) + relZ) >> CACHE_WIDTH_SHIFT;
                        int cacheRelX = ((x << 2) + relX) & (CACHE_WIDTH - 1);
                        int cacheRelZ = ((z << 2) + relZ) & (CACHE_WIDTH - 1);
                        double[] cacheData = futures.get(new CacheIndex(cacheX, cacheZ)).join().flatCaches();
                        flatCaches[cacheIndex * flatArea + relX * flatSizeZ + relZ]
                                = cacheData[(cacheIndex << (CACHE_WIDTH_SHIFT << 1))
                                + (cacheRelX << CACHE_WIDTH_SHIFT) + cacheRelZ];
                    }
                }
            }
            return new RawCacheEntry(surfaceHeights, flatCaches);
        });
    }

    private record CacheIndex(int x, int z) {
        public long toLong() { return ((long) x << 32) | (z & 0xffffffffL); }
    }

    public record RawCacheEntry(int[] surfaceHeights, double[] flatCaches) {}
    public record AreaCacheEntry(int chunkX, int chunkZ, int sizeX, int sizeZ, int[] surfaceHeights, double[] flatCaches) {}
}
