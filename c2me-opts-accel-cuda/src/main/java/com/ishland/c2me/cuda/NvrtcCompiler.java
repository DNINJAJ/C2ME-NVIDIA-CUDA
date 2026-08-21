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
import java.nio.file.Files;
import java.nio.file.Path;

/** Small NVRTC binding used to compile generated CUDA C into PTX. */
public final class NvrtcCompiler {
    private static final ValueLayout.OfInt NATIVE_INT = ValueLayout.JAVA_INT.withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfLong NATIVE_LONG = ValueLayout.JAVA_LONG.withOrder(ByteOrder.nativeOrder());
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena LIBRARY_ARENA = Arena.global();
    private static final MethodHandle CREATE_PROGRAM;
    private static final MethodHandle COMPILE_PROGRAM;
    private static final MethodHandle GET_PTX_SIZE;
    private static final MethodHandle GET_PTX;
    private static final MethodHandle GET_LOG_SIZE;
    private static final MethodHandle GET_LOG;
    private static final MethodHandle DESTROY_PROGRAM;

    static {
        SymbolLookup lookup = loadLibrary();
        CREATE_PROGRAM = downcall(lookup, "nvrtcCreateProgram", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        COMPILE_PROGRAM = downcall(lookup, "nvrtcCompileProgram", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, NATIVE_INT, ValueLayout.ADDRESS));
        GET_PTX_SIZE = downcall(lookup, "nvrtcGetPTXSize", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GET_PTX = downcall(lookup, "nvrtcGetPTX", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GET_LOG_SIZE = downcall(lookup, "nvrtcGetProgramLogSize", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GET_LOG = downcall(lookup, "nvrtcGetProgramLog", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        DESTROY_PROGRAM = downcall(lookup, "nvrtcDestroyProgram", FunctionDescriptor.of(
                NATIVE_INT, ValueLayout.ADDRESS));
    }

    private NvrtcCompiler() {
    }

    public static String compileToPtx(String source, String sourceName) {
        return compileToPtx(source, sourceName, "--gpu-architecture=compute_120", "--device-as-default-execution-space", "--std=c++20");
    }

    public static String compileToPtx(String source, String sourceName, String... options) {
        if (source == null || sourceName == null) {
            throw new NullPointerException();
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment programOut = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment sourceSegment = arena.allocateFrom(source, StandardCharsets.UTF_8);
            MemorySegment nameSegment = arena.allocateFrom(sourceName, StandardCharsets.UTF_8);
            int result = call(CREATE_PROGRAM, programOut, sourceSegment, nameSegment, 0,
                    MemorySegment.NULL, MemorySegment.NULL);
            checkResult(result, "nvrtcCreateProgram");

            MemorySegment program = programOut.get(ValueLayout.ADDRESS, 0);
            try {
                MemorySegment optionPointers = arena.allocate(ValueLayout.ADDRESS.byteSize() * options.length,
                        ValueLayout.ADDRESS.byteAlignment());
                for (int i = 0; i < options.length; i++) {
                    MemorySegment option = arena.allocateFrom(options[i], StandardCharsets.UTF_8);
                    optionPointers.set(ValueLayout.ADDRESS, (long) i * ValueLayout.ADDRESS.byteSize(), option);
                }
                result = call(COMPILE_PROGRAM, program, options.length, optionPointers);
                if (result != 0) {
                    throw new IllegalStateException("NVRTC compilation failed (" + result + "): " + getLog(program));
                }

                MemorySegment ptxSize = arena.allocate(NATIVE_LONG);
                checkResult(call(GET_PTX_SIZE, program, ptxSize), "nvrtcGetPTXSize");
                long size = ptxSize.get(NATIVE_LONG, 0);
                if (size <= 0 || size > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Invalid PTX size returned by NVRTC: " + size);
                }
                MemorySegment ptx = arena.allocate(size, 1);
                checkResult(call(GET_PTX, program, ptx), "nvrtcGetPTX");
                return ptx.getString(0);
            } finally {
                call(DESTROY_PROGRAM, programOut);
            }
        } catch (Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("NVRTC invocation failed", t);
        }
    }

    private static String getLog(MemorySegment program) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sizeOut = arena.allocate(NATIVE_LONG);
            checkResult(call(GET_LOG_SIZE, program, sizeOut), "nvrtcGetProgramLogSize");
            long size = sizeOut.get(NATIVE_LONG, 0);
            if (size <= 0 || size > Integer.MAX_VALUE) {
                return "<no NVRTC log>";
            }
            MemorySegment log = arena.allocate(size, 1);
            checkResult(call(GET_LOG, program, log), "nvrtcGetProgramLog");
            return log.getString(0);
        }
    }

    private static SymbolLookup loadLibrary() {
        String configuredDir = System.getProperty("c2me.cuda.nvrtc.dir");
        Path directory = configuredDir == null || configuredDir.isBlank()
                ? findDefaultDirectory()
                : Path.of(configuredDir);
        Path builtins = directory.resolve("nvrtc-builtins64_133.dll");
        Path nvrtc = directory.resolve("nvrtc64_130_0.dll");
        if (!Files.isRegularFile(builtins) || !Files.isRegularFile(nvrtc)) {
            throw new IllegalStateException("NVRTC DLLs not found in " + directory);
        }
        System.load(builtins.toAbsolutePath().toString());
        System.load(nvrtc.toAbsolutePath().toString());
        return SymbolLookup.loaderLookup();
    }

    private static Path findDefaultDirectory() {
        String[] candidates = {
                "c2me-cuda/nvrtc",
                "cuda-runtime/nvrtc-13.3.33/cuda_nvrtc-windows-x86_64-13.3.33-archive/bin/x64",
                "../cuda-runtime/nvrtc-13.3.33/cuda_nvrtc-windows-x86_64-13.3.33-archive/bin/x64"
        };
        for (String candidate : candidates) {
            Path directory = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(directory.resolve("nvrtc-builtins64_133.dll") )
                    && Files.isRegularFile(directory.resolve("nvrtc64_130_0.dll"))) {
                return directory;
            }
        }
        throw new IllegalStateException("NVRTC DLLs not found. Set -Dc2me.cuda.nvrtc.dir to their directory");
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(lookup.find(name).orElseThrow(
                () -> new IllegalStateException("NVRTC symbol not found: " + name)), descriptor);
    }

    private static int call(MethodHandle handle, Object... arguments) throws Throwable {
        return (int) handle.invokeWithArguments(arguments);
    }

    private static void checkResult(int result, String operation) {
        if (result != 0) {
            throw new IllegalStateException(operation + " returned NVRTC error code " + result);
        }
    }
}
