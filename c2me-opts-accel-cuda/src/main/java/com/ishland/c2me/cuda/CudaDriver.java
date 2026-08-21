/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.ishland.c2me.cuda;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

/** Minimal CUDA Driver API binding used by the addon bootstrap. */
public final class CudaDriver {
    private static final ValueLayout.OfInt NATIVE_INT = ValueLayout.JAVA_INT.withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfLong NATIVE_LONG = ValueLayout.JAVA_LONG.withOrder(ByteOrder.nativeOrder());
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena LIBRARY_ARENA = Arena.global();

    private final MethodHandle cuDeviceGetCount;
    private final MethodHandle cuDeviceGetName;
    private final MethodHandle cuModuleLoadData;
    private final MethodHandle cuModuleGetFunction;
    private final MethodHandle cuMemAlloc;
    private final MethodHandle cuMemFree;
    private final MethodHandle cuMemcpyHtoD;
    private final MethodHandle cuMemcpyDtoH;
    private final MethodHandle cuLaunchKernel;
    private final MethodHandle cuCtxSynchronize;
    private final MethodHandle cuStreamCreate;
    private final MethodHandle cuStreamDestroy;
    private final MethodHandle cuStreamSynchronize;
    private final MethodHandle cuCtxSetCurrent;
    private final long contextAddress;

    private CudaDriver(MethodHandle cuDeviceGetCount, MethodHandle cuDeviceGetName,
                       MethodHandle cuModuleLoadData, MethodHandle cuModuleGetFunction,
                       MethodHandle cuMemAlloc, MethodHandle cuMemFree,
                       MethodHandle cuMemcpyHtoD, MethodHandle cuMemcpyDtoH,
                       MethodHandle cuLaunchKernel, MethodHandle cuCtxSynchronize,
                       MethodHandle cuStreamCreate, MethodHandle cuStreamDestroy,
                       MethodHandle cuStreamSynchronize,
                       MethodHandle cuCtxSetCurrent, long contextAddress) {
        this.cuDeviceGetCount = cuDeviceGetCount;
        this.cuDeviceGetName = cuDeviceGetName;
        this.cuModuleLoadData = cuModuleLoadData;
        this.cuModuleGetFunction = cuModuleGetFunction;
        this.cuMemAlloc = cuMemAlloc;
        this.cuMemFree = cuMemFree;
        this.cuMemcpyHtoD = cuMemcpyHtoD;
        this.cuMemcpyDtoH = cuMemcpyDtoH;
        this.cuLaunchKernel = cuLaunchKernel;
        this.cuCtxSynchronize = cuCtxSynchronize;
        this.cuStreamCreate = cuStreamCreate;
        this.cuStreamDestroy = cuStreamDestroy;
        this.cuStreamSynchronize = cuStreamSynchronize;
        this.cuCtxSetCurrent = cuCtxSetCurrent;
        this.contextAddress = contextAddress;
    }

    public static CudaDriver load() {
        SymbolLookup lookup;
        try {
            lookup = SymbolLookup.libraryLookup("nvcuda", LIBRARY_ARENA);
        } catch (IllegalCallerException | UnsatisfiedLinkError e) {
            throw new IllegalStateException("CUDA driver library nvcuda.dll is not available", e);
        }

        invokeInit(lookup);
        long contextAddress = initializeContext(lookup);
        return new CudaDriver(
                downcall(lookup, "cuDeviceGetCount", FunctionDescriptor.of(NATIVE_INT, ValueLayout.ADDRESS)),
                downcall(lookup, "cuDeviceGetName", FunctionDescriptor.of(
                        NATIVE_INT, ValueLayout.ADDRESS, NATIVE_INT, NATIVE_INT)),
                downcall(lookup, "cuModuleLoadData", FunctionDescriptor.of(
                        NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                downcall(lookup, "cuModuleGetFunction", FunctionDescriptor.of(
                        NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                downcall(lookup, "cuMemAlloc_v2", FunctionDescriptor.of(
                        NATIVE_INT, ValueLayout.ADDRESS, NATIVE_LONG)),
                downcall(lookup, "cuMemFree_v2", FunctionDescriptor.of(NATIVE_INT, NATIVE_LONG)),
                downcall(lookup, "cuMemcpyHtoD_v2", FunctionDescriptor.of(
                        NATIVE_INT, NATIVE_LONG, ValueLayout.ADDRESS, NATIVE_LONG)),
                downcall(lookup, "cuMemcpyDtoH_v2", FunctionDescriptor.of(
                        NATIVE_INT, ValueLayout.ADDRESS, NATIVE_LONG, NATIVE_LONG)),
                downcall(lookup, "cuLaunchKernel", FunctionDescriptor.of(
                        NATIVE_INT, ValueLayout.ADDRESS,
                        NATIVE_INT, NATIVE_INT, NATIVE_INT,
                        NATIVE_INT, NATIVE_INT, NATIVE_INT,
                        NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                downcall(lookup, "cuCtxSynchronize", FunctionDescriptor.of(NATIVE_INT)),
                downcall(lookup, "cuStreamCreate", FunctionDescriptor.of(
                        NATIVE_INT, ValueLayout.ADDRESS, NATIVE_INT)),
                downcall(lookup, "cuStreamDestroy_v2", FunctionDescriptor.of(NATIVE_INT, NATIVE_LONG)),
                downcall(lookup, "cuStreamSynchronize", FunctionDescriptor.of(NATIVE_INT, NATIVE_LONG)),
                downcall(lookup, "cuCtxSetCurrent", FunctionDescriptor.of(NATIVE_INT, ValueLayout.ADDRESS)),
                contextAddress
        );
    }

    /** Makes the retained primary context current on the calling thread. */
    private void ensureContext() {
        try {
            checkResult(call(cuCtxSetCurrent, MemorySegment.ofAddress(contextAddress)), "cuCtxSetCurrent");
        } catch (Throwable t) {
            throw propagate("cuCtxSetCurrent failed", t);
        }
    }

    public int deviceCount() {
        ensureContext();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment count = arena.allocate(NATIVE_INT);
            checkResult((int) cuDeviceGetCount.invokeExact(count), "cuDeviceGetCount");
            return count.get(NATIVE_INT, 0);
        } catch (Throwable t) {
            throw propagate("cuDeviceGetCount failed", t);
        }
    }

    public String deviceName(int deviceIndex) {
        ensureContext();
        if (deviceIndex < 0) {
            throw new IllegalArgumentException("deviceIndex must be non-negative");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocate(256, 1);
            checkResult((int) cuDeviceGetName.invokeExact(name, 256, deviceIndex), "cuDeviceGetName");
            return name.getString(0);
        } catch (Throwable t) {
            throw propagate("cuDeviceGetName failed", t);
        }
    }

    public long loadModule(String ptx) {
        ensureContext();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment moduleOut = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment ptxSegment = arena.allocateFrom(ptx, StandardCharsets.US_ASCII);
            checkResult(call(cuModuleLoadData, moduleOut, ptxSegment), "cuModuleLoadData");
            return moduleOut.get(ValueLayout.ADDRESS, 0).address();
        } catch (Throwable t) {
            throw propagate("cuModuleLoadData failed", t);
        }
    }

    public long getFunction(long module, String name) {
        ensureContext();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment functionOut = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment moduleSegment = MemorySegment.ofAddress(module);
            MemorySegment nameSegment = arena.allocateFrom(name, StandardCharsets.US_ASCII);
            checkResult(call(cuModuleGetFunction, functionOut, moduleSegment, nameSegment), "cuModuleGetFunction");
            return functionOut.get(ValueLayout.ADDRESS, 0).address();
        } catch (Throwable t) {
            throw propagate("cuModuleGetFunction failed", t);
        }
    }

    public long allocate(long bytes) {
        ensureContext();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(NATIVE_LONG);
            checkResult(call(cuMemAlloc, out, bytes), "cuMemAlloc");
            return out.get(NATIVE_LONG, 0);
        } catch (Throwable t) {
            throw propagate("cuMemAlloc failed", t);
        }
    }

    public void free(long address) {
        if (address == 0L) return;
        ensureContext();
        try {
            checkResult(call(cuMemFree, address), "cuMemFree");
        } catch (Throwable t) {
            throw propagate("cuMemFree failed", t);
        }
    }

    public void copyHostToDevice(long deviceAddress, ByteBuffer source) {
        ensureContext();
        ByteBuffer view = source.duplicate();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate(view.remaining(), 1);
            MemorySegment.copy(MemorySegment.ofBuffer(view), 0, host, 0, view.remaining());
            checkResult(call(cuMemcpyHtoD, deviceAddress, host, view.remaining()), "cuMemcpyHtoD");
        } catch (Throwable t) {
            throw propagate("cuMemcpyHtoD failed", t);
        }
    }

    public void copyDeviceToHost(long deviceAddress, ByteBuffer destination) {
        ensureContext();
        ByteBuffer view = destination.duplicate();
        int bytes = view.remaining();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate(bytes, 1);
            checkResult(call(cuMemcpyDtoH, host, deviceAddress, bytes), "cuMemcpyDtoH");
            MemorySegment.copy(host, 0, MemorySegment.ofBuffer(view), 0, bytes);
            destination.position(destination.position() + bytes);
        } catch (Throwable t) {
            throw propagate("cuMemcpyDtoH failed", t);
        }
    }

    public void launch(long function, int gridX, int gridY, int gridZ,
                       int blockX, int blockY, int blockZ, long... arguments) {
        launch(0L, function, gridX, gridY, gridZ, blockX, blockY, blockZ, arguments);
    }

    public void launch(long stream, long function, int gridX, int gridY, int gridZ,
                       int blockX, int blockY, int blockZ, long... arguments) {
        ensureContext();
        try (Arena arena = Arena.ofConfined()) {
            long pointerSize = ValueLayout.ADDRESS.byteSize();
            MemorySegment kernelParams = arena.allocate(pointerSize * arguments.length, ValueLayout.ADDRESS.byteAlignment());
            for (int i = 0; i < arguments.length; i++) {
                long value = arguments[i];
                MemorySegment argument = arena.allocate(NATIVE_LONG);
                argument.set(NATIVE_LONG, 0, value);
                kernelParams.set(ValueLayout.ADDRESS, pointerSize * i, argument);
            }
            checkResult(call(cuLaunchKernel, MemorySegment.ofAddress(function),
                    gridX, gridY, gridZ, blockX, blockY, blockZ, 0,
                    stream == 0L ? MemorySegment.NULL : MemorySegment.ofAddress(stream),
                    kernelParams, MemorySegment.NULL), "cuLaunchKernel");
        } catch (Throwable t) {
            throw propagate("cuLaunchKernel failed", t);
        }
    }

    public void synchronize() {
        ensureContext();
        try {
            checkResult(call(cuCtxSynchronize), "cuCtxSynchronize");
        } catch (Throwable t) {
            throw propagate("cuCtxSynchronize failed", t);
        }
    }

    public long createStream() {
        ensureContext();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stream = arena.allocate(NATIVE_LONG);
            checkResult(call(cuStreamCreate, stream, 0), "cuStreamCreate");
            return stream.get(NATIVE_LONG, 0);
        } catch (Throwable t) {
            throw propagate("cuStreamCreate failed", t);
        }
    }

    public void destroyStream(long stream) {
        if (stream == 0L) return;
        ensureContext();
        try {
            checkResult(call(cuStreamDestroy, stream), "cuStreamDestroy");
        } catch (Throwable t) {
            throw propagate("cuStreamDestroy failed", t);
        }
    }

    public void synchronize(long stream) {
        if (stream == 0L) {
            synchronize();
            return;
        }
        ensureContext();
        try {
            checkResult(call(cuStreamSynchronize, stream), "cuStreamSynchronize");
        } catch (Throwable t) {
            throw propagate("cuStreamSynchronize failed", t);
        }
    }

    public float[] addOne(float[] input) {
        ensureContext();
        if (input == null) {
            throw new NullPointerException("input");
        }
        if (input.length == 0) {
            return new float[0];
        }

        String ptx = """
                .version 8.0
                .target sm_80
                .address_size 64

                .visible .entry add_one(
                    .param .u64 input_ptr,
                    .param .u64 output_ptr,
                    .param .u32 length
                ) {
                    .reg .pred %p;
                    .reg .b32 %r<6>;
                    .reg .b64 %rd<6>;
                    .reg .f32 %f;

                    ld.param.u64 %rd1, [input_ptr];
                    ld.param.u64 %rd2, [output_ptr];
                    ld.param.u32 %r1, [length];
                    mov.u32 %r2, %tid.x;
                    mov.u32 %r3, %ctaid.x;
                    mov.u32 %r4, %ntid.x;
                    mad.lo.u32 %r2, %r3, %r4, %r2;
                    setp.ge.u32 %p, %r2, %r1;
                    @%p bra DONE;
                    mul.wide.u32 %rd3, %r2, 4;
                    add.s64 %rd4, %rd1, %rd3;
                    ld.global.f32 %f, [%rd4];
                    add.f32 %f, %f, 1.0;
                    add.s64 %rd5, %rd2, %rd3;
                    st.global.f32 [%rd5], %f;
                DONE:
                    ret;
                }
                """;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment moduleOut = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment ptxSegment = arena.allocateFrom(ptx, StandardCharsets.US_ASCII);
            checkResult(call(cuModuleLoadData, moduleOut, ptxSegment), "cuModuleLoadData");
            MemorySegment module = moduleOut.get(ValueLayout.ADDRESS, 0);

            MemorySegment functionOut = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment functionName = arena.allocateFrom("add_one", StandardCharsets.US_ASCII);
            checkResult(call(cuModuleGetFunction, functionOut, module, functionName), "cuModuleGetFunction");
            MemorySegment function = functionOut.get(ValueLayout.ADDRESS, 0);

            long bytes = (long) input.length * Float.BYTES;
            MemorySegment hostInput = arena.allocate(bytes, Float.BYTES);
            MemorySegment hostOutput = arena.allocate(bytes, Float.BYTES);
            for (int i = 0; i < input.length; i++) {
                hostInput.set(ValueLayout.JAVA_FLOAT, (long) i * Float.BYTES, input[i]);
            }

            MemorySegment inputDeviceOut = arena.allocate(NATIVE_LONG);
            MemorySegment outputDeviceOut = arena.allocate(NATIVE_LONG);
            checkResult(call(cuMemAlloc, inputDeviceOut, bytes), "cuMemAlloc(input)");
            checkResult(call(cuMemAlloc, outputDeviceOut, bytes), "cuMemAlloc(output)");
            long inputDevice = inputDeviceOut.get(NATIVE_LONG, 0);
            long outputDevice = outputDeviceOut.get(NATIVE_LONG, 0);

            try {
                checkResult(call(cuMemcpyHtoD, inputDevice, hostInput, bytes), "cuMemcpyHtoD");

                MemorySegment inputArg = arena.allocate(NATIVE_LONG);
                MemorySegment outputArg = arena.allocate(NATIVE_LONG);
                MemorySegment lengthArg = arena.allocate(NATIVE_INT);
                inputArg.set(NATIVE_LONG, 0, inputDevice);
                outputArg.set(NATIVE_LONG, 0, outputDevice);
                lengthArg.set(NATIVE_INT, 0, input.length);

                MemorySegment kernelParams = arena.allocate(ValueLayout.ADDRESS.byteSize() * 3L,
                        ValueLayout.ADDRESS.byteAlignment());
                kernelParams.set(ValueLayout.ADDRESS, 0, inputArg);
                kernelParams.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(), outputArg);
                kernelParams.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize() * 2L, lengthArg);

                checkResult(call(cuLaunchKernel, function,
                        (input.length + 31) / 32, 1, 1,
                        32, 1, 1, 0,
                        MemorySegment.NULL, kernelParams, MemorySegment.NULL), "cuLaunchKernel");
                checkResult(call(cuCtxSynchronize), "cuCtxSynchronize");
                checkResult(call(cuMemcpyDtoH, hostOutput, outputDevice, bytes), "cuMemcpyDtoH");

                float[] output = new float[input.length];
                for (int i = 0; i < output.length; i++) {
                    output[i] = hostOutput.get(ValueLayout.JAVA_FLOAT, (long) i * Float.BYTES);
                }
                return output;
            } finally {
                checkResult(call(cuMemFree, inputDevice), "cuMemFree(input)");
                checkResult(call(cuMemFree, outputDevice), "cuMemFree(output)");
            }
        } catch (Throwable t) {
            throw propagate("CUDA vector kernel failed", t);
        }
    }

    private static void invokeInit(SymbolLookup lookup) {
        MethodHandle cuInit = downcall(lookup, "cuInit", FunctionDescriptor.of(NATIVE_INT, NATIVE_INT));
        try {
            checkResult((int) cuInit.invokeExact(0), "cuInit");
        } catch (Throwable t) {
            throw propagate("cuInit failed", t);
        }
    }

    private static long initializeContext(SymbolLookup lookup) {
        MethodHandle cuDeviceGet = downcall(lookup, "cuDeviceGet", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, NATIVE_INT));
        MethodHandle cuDevicePrimaryCtxRetain = downcall(lookup, "cuDevicePrimaryCtxRetain", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, NATIVE_INT));
        MethodHandle cuCtxSetCurrent = downcall(lookup, "cuCtxSetCurrent", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment device = arena.allocate(NATIVE_INT);
            MemorySegment context = arena.allocate(ValueLayout.ADDRESS);
            checkResult(call(cuDeviceGet, device, 0), "cuDeviceGet");
            checkResult(call(cuDevicePrimaryCtxRetain, context, device.get(NATIVE_INT, 0)),
                    "cuDevicePrimaryCtxRetain");
            long contextAddress = context.get(ValueLayout.ADDRESS, 0).address();
            checkResult(call(cuCtxSetCurrent, MemorySegment.ofAddress(contextAddress)), "cuCtxSetCurrent");
            return contextAddress;
        } catch (Throwable t) {
            throw propagate("CUDA context initialization failed", t);
        }
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(lookup.find(name).orElseThrow(
                () -> new IllegalStateException("CUDA symbol not found: " + name)), descriptor);
    }

    private static int call(MethodHandle handle, Object... arguments) throws Throwable {
        return (int) handle.invokeWithArguments(arguments);
    }

    private static void checkResult(int result, String operation) {
        if (result != 0) {
            throw new IllegalStateException(operation + " returned CUDA error code " + result);
        }
    }

    private static RuntimeException propagate(String message, Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(message, cause);
    }
}
