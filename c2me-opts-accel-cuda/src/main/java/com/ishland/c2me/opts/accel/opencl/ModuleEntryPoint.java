/*
 * All Rights Reserved
 *
 * Copyright (c) 2025-2026 ishland
 *
 * All rights reserved. Do not redistribute.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.ishland.c2me.opts.accel.opencl;

import com.ishland.c2me.base.common.config.ConfigSystem;
import com.ishland.c2me.opts.accel.opencl.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleEntryPoint.class);

    private static final boolean enabled = new ConfigSystem.ConfigAccessor()
            .key("openclAccel.enabled")
            .comment("""
                    Enable CUDA acceleration for world generation.
                    """)
            .getBoolean(true, false);

    static {
        Config.init();
        LOGGER.info("C2ME CUDA acceleration module initialized; OpenCL runtime is disabled");
    }

}
