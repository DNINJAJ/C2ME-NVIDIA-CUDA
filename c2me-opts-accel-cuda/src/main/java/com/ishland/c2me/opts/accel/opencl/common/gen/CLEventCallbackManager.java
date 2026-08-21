/*
 * All Rights Reserved
 *
 * Copyright (c) 2025-2026 ishland
 *
 * All rights reserved. Do not redistribute.
 */

package com.ishland.c2me.opts.accel.opencl.common.gen;

import org.lwjgl.opencl.CLEventCallbackI;

import java.io.Closeable;

/** Compatibility facade: CUDA completion is synchronized explicitly by the device. */
public final class CLEventCallbackManager implements Closeable {
    public void registerCallback(long event, int status, CLEventCallbackI callback) {
        callback.invoke(event, 0, 0);
    }

    @Override
    public void close() {
    }
}
