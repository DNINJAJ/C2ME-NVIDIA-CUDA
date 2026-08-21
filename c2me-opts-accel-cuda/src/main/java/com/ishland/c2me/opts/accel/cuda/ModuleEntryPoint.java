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

package com.ishland.c2me.opts.accel.cuda;

import com.ishland.c2me.cuda.CudaDriver;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModuleEntryPoint implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("c2me-opts-accel-cuda");

    @Override
    public void onInitialize() {
        try {
            CudaDriver driver = CudaDriver.load();
            int deviceCount = driver.deviceCount();
            if (deviceCount == 0) {
                LOGGER.warn("CUDA driver initialized but no CUDA devices were found");
            } else {
                LOGGER.info("CUDA backend initialized: {} device(s), first device={}",
                        deviceCount, driver.deviceName(0));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("CUDA backend is unavailable; CUDA acceleration remains disabled", exception);
        }
    }
}
