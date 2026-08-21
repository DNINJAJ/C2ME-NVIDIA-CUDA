/*
 * All Rights Reserved
 *
 * Copyright (c) 2025-2026 ishland
 *
 * All rights reserved. Do not redistribute.
 */

package com.ishland.c2me.opts.accel.opencl.common.enumeration;

import com.ishland.c2me.cuda.CudaDriver;
import com.ishland.c2me.opts.accel.opencl.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Device discovery kept under the legacy class name so the original mixins remain compatible. */
public final class OpenCLDeviceLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCLDeviceLocator.class);

    private OpenCLDeviceLocator() {
    }

    public static boolean isAvailable() {
        try {
            return CudaDriver.load().deviceCount() > 0;
        } catch (Throwable t) {
            LOGGER.warn("CUDA driver is unavailable; GPU world generation will stay disabled", t);
            if (!Config.allowIncompatibilityFallback) throw t;
            return false;
        }
    }

    public static List<OpenCLDeviceMetadata> enumerateAll() {
        List<OpenCLDeviceMetadata> result = new ArrayList<>();
        final CudaDriver driver;
        try {
            driver = CudaDriver.load();
        } catch (Throwable t) {
            LOGGER.warn("Failed to initialize CUDA", t);
            if (!Config.allowIncompatibilityFallback) throw t;
            return result;
        }
        for (int i = 0; i < driver.deviceCount(); i++) {
            if (!Config.allowGPUDevices) continue;
            String name = driver.deviceName(i);
            UUID deviceUuid = UUID.nameUUIDFromBytes(("cuda-device:" + i + ":" + name).getBytes(StandardCharsets.UTF_8));
            UUID driverUuid = UUID.nameUUIDFromBytes(("nvidia-driver:" + name).getBytes(StandardCharsets.UTF_8));
            if (!Config.deviceUUIDWhitelist.isEmpty() && !Config.deviceUUIDWhitelist.contains(deviceUuid)) continue;
            if (Config.deviceUUIDBlacklist.contains(deviceUuid)) continue;
            LOGGER.info("Found CUDA device {} ({})", name, i);
            result.add(new OpenCLDeviceMetadata(i, name, deviceUuid, driverUuid));
        }
        return result;
    }
}
