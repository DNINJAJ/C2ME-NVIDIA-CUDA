/*
 * All Rights Reserved
 *
 * Copyright (c) 2025-2026 ishland
 *
 * All rights reserved. Do not redistribute.
 */

package com.ishland.c2me.opts.accel.opencl.common.gen;

import com.ishland.c2me.cuda.CudaDriver;
import com.ishland.c2me.cuda.CudaSourceTranspiler;
import com.ishland.c2me.cuda.NvrtcCompiler;
import com.ishland.c2me.opts.accel.opencl.common.Config;
import com.ishland.c2me.opts.accel.opencl.common.compiler.GeneratedCLSource;
import com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen;
import com.ishland.c2me.opts.accel.opencl.common.enumeration.OpenCLDeviceMetadata;
import com.ishland.c2me.opts.accel.opencl.common.gen.cache.CLBufferCache;
import com.ishland.c2me.opts.accel.opencl.common.workarounds.Workarounds;
import com.ishland.flowsched.util.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** CUDA implementation behind the original OpenCL-facing world-generation API. */
public final class OpenCLDevice implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCLDevice.class);
    private static final AtomicInteger DEVICE_COUNTER = new AtomicInteger();

    private final CLServerGlobalContext globalContext;
    private final OpenCLDeviceMetadata metadata;
    private final CudaDriver cuda;
    private final ExecutorService executor;
    private final CLBufferCache bufferCache;
    private final CLEventCallbackManager eventCallbackManager = new CLEventCallbackManager();
    private final AtomicInteger permits;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final String deviceDescription;

    public OpenCLDevice(CLServerGlobalContext globalContext, OpenCLDeviceMetadata metadata) {
        this.globalContext = globalContext;
        this.metadata = metadata;
        this.cuda = CudaDriver.load();
        this.deviceDescription = "CUDA Device " + metadata.deviceName;
        // The OpenCL implementation can keep up to maxConcurrentTasksPerDevice
        // generation jobs in flight. CUDA uses one stream per borrowed queue,
        // so capping this at eight artificially serialized the workload and
        // left the GPU underfed on large Chunky jobs.
        // CUDA command submission and host/device transfers are a little more
        // expensive than the OpenCL queue path. Keep extra work in flight so
        // those gaps can overlap instead of starving the GPU. The configured
        // OpenCL limit remains the baseline; the extra sixteen slots are only
        // used by this CUDA backend.
        int workerCount = Math.max(1, Config.maxConcurrentTasksPerDevice + 16);
        this.permits = new AtomicInteger(workerCount);
        String threadName = "c2me-cuda-%d-%s".formatted(DEVICE_COUNTER.getAndIncrement(), metadata.deviceName.replaceAll("[^a-zA-Z0-9]", "_"));
        this.executor = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        this.bufferCache = new CLBufferCache(this.executor, this.cuda::free);
        LOGGER.info("Initializing {}", this.deviceDescription);
    }

    public CompletableFuture<EnumMap<OpenCLCGen.ProgramType, Long>> compileProgramAsync(String desc, GeneratedCLSource source) {
        assertOpen();
        return CompletableFuture.supplyAsync(() -> {
            EnumMap<OpenCLCGen.ProgramType, Long> result = new EnumMap<>(OpenCLCGen.ProgramType.class);
            String cudaSource = CudaSourceTranspiler.transpile(source.getGeneratedSource());
            for (OpenCLCGen.ProgramType type : OpenCLCGen.ProgramType.values()) {
                StringBuilder options = new StringBuilder("--gpu-architecture=compute_120 --device-as-default-execution-space --std=c++20");
                options.append(" -DDF_COMPILE_").append(type.name()).append("=1");
                if (source.getDefines() != null) {
                    source.getDefines().forEach((key, value) -> options.append(" -D").append(key).append("=").append(value));
                }
                LOGGER.info("Compiling CUDA PTX {} for {} on {}", type, desc, deviceDescription);
                String ptx = NvrtcCompiler.compileToPtx(cudaSource, desc + "_" + type.name(), options.toString().split(" "));
                result.put(type, cuda.loadModule(ptx));
            }
            return result;
        }, executor);
    }

    public long getKernel(long module, String name) {
        return cuda.getFunction(module, name);
    }

    public long allocate(int bytes) {
        return cuda.allocate(bytes);
    }

    public long allocate(long bytes) {
        return cuda.allocate(bytes);
    }

    public void free(long address) {
        cuda.free(address);
    }

    public void copyToDevice(long address, ByteBuffer data) {
        cuda.copyHostToDevice(address, data);
    }

    public void copyToDevice(long stream, long address, ByteBuffer data) {
        cuda.copyHostToDevice(stream, address, data);
    }

    public void copyFromDevice(long address, ByteBuffer data) {
        cuda.copyDeviceToHost(address, data);
    }

    public void copyFromDevice(long stream, long address, ByteBuffer data) {
        cuda.copyDeviceToHost(stream, address, data);
    }

    public void launch(long function, int gridX, int gridY, int gridZ, int blockX, int blockY, int blockZ, long... args) {
        cuda.launch(function, gridX, gridY, gridZ, blockX, blockY, blockZ, args);
    }

    public void launch(long stream, long function, int gridX, int gridY, int gridZ,
                       int blockX, int blockY, int blockZ, long... args) {
        cuda.launch(stream, function, gridX, gridY, gridZ, blockX, blockY, blockZ, args);
    }

    public void synchronize() {
        cuda.synchronize();
    }

    public long createStream() {
        return cuda.createStream();
    }

    public void destroyStream(long stream) {
        cuda.destroyStream(stream);
    }

    public void synchronize(long stream) {
        cuda.synchronize(stream);
    }

    private void assertOpen() {
        if (!open.get()) throw new IllegalStateException("CUDA device is closed: " + deviceDescription);
    }

    public BorrowedCommandQueue borrowCommandQueue() {
        synchronized (permits) {
            if (permits.get() <= 0) return null;
            permits.decrementAndGet();
            return new BorrowedCommandQueue();
        }
    }

    private void returnCommandQueue() {
        synchronized (permits) {
            permits.incrementAndGet();
        }
        globalContext.signalNotEmpty();
    }

    public int getPermits() { return permits.get(); }
    public long getContext() { return 0L; }
    public OpenCLDeviceMetadata getMetadata() { return metadata; }
    public Executor getExecutor() { return executor; }
    public CLBufferCache getBufferCache() { return bufferCache; }
    public CLEventCallbackManager getEventCallbackManager() { return eventCallbackManager; }
    public Set<Workarounds.Reference> getWorkarounds() { return Set.of(); }
    public long allocateAndCopy(byte[] bytes) {
        long address = allocate(bytes.length);
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        copyToDevice(address, buffer);
        return address;
    }

    @Override
    public String toString() { return deviceDescription; }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            cuda.synchronize();
            bufferCache.close();
            eventCallbackManager.close();
            executor.shutdown();
        }
    }

    public final class BorrowedCommandQueue implements Closeable {
        private final AtomicBoolean queueOpen = new AtomicBoolean(true);
        private final long stream = createStream();
        public long getCommandQueue() { return 0L; }
        public long getContext() { return 0L; }
        public long getStream() { return stream; }
        public OpenCLDevice getDevice() { return OpenCLDevice.this; }
        @Override public void close() {
            if (queueOpen.compareAndSet(true, false)) {
                destroyStream(stream);
                returnCommandQueue();
            }
            else LOGGER.warn("Attempted to close CUDA queue twice for {}", deviceDescription);
        }
    }
}
