package com.ishland.c2me.opts.accel.opencl.common.shader_cache;

import com.ishland.c2me.opts.accel.opencl.common.compiler.OpenCLCGen;
import com.ishland.c2me.opts.accel.opencl.common.enumeration.OpenCLDeviceMetadata;

import java.util.EnumMap;

/**
 * Compatibility facade for the original API. CUDA uses NVRTC/PTX and does not
 * consume OpenCL binary shader archives, so this manager deliberately performs
 * no filesystem or OpenCL work.
 */
public final class ShaderCacheManager {
    public ShaderCacheManager() {
    }

    public void tryCacheDirs(long context, OpenCLDeviceMetadata metadata,
                             EnumMap<OpenCLCGen.ProgramType, Long> map, String... paths) {
        // CUDA modules are compiled by OpenCLDevice.compileProgramAsync().
    }

    public byte[] tryCache(String path) {
        return null;
    }
}
