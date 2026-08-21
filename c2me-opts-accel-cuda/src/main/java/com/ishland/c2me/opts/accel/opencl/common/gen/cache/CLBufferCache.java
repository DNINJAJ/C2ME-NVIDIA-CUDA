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

package com.ishland.c2me.opts.accel.opencl.common.gen.cache;

import com.ishland.c2me.base.common.util.MemoryUtil;
import com.ishland.c2me.opts.accel.opencl.common.Config;
import it.unimi.dsi.fastutil.longs.Long2LongFunction;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongConsumer;

public class CLBufferCache {

    private static final int MAX_CACHE_SIZE = Config.maxConcurrentTasksPerDevice + 4;

    private final int[] cacheBufferSizes = new int[Type.values().length];
    private final LongArrayList[] caches = new LongArrayList[Type.values().length];

    private final LongConsumer releaser;

    {
        for (int i = 0; i < caches.length; i++) {
            this.caches[i] = new LongArrayList();
        }
    }

    public CLBufferCache(Object ignoredOwner) {
        this(ignoredOwner, ignored -> { });
    }

    public CLBufferCache(Object ignoredOwner, LongConsumer releaser) {
        this.releaser = Objects.requireNonNull(releaser);
    }

    public synchronized BufferEntry allocate(Type type, int size, Long2LongFunction allocator) {
        int cacheBufferSize = this.cacheBufferSizes[type.ordinal()];
        if (cacheBufferSize < size) {
            this.cacheBufferSizes[type.ordinal()] = cacheBufferSize = MemoryUtil.roundUp(size, 4096);
            clearCache0(this.caches[type.ordinal()]);
        }
        long l = tryBorrow0(type);
        if (l == 0L) {
            l = allocator.get(cacheBufferSize);
        }
        return new BufferEntry(l, cacheBufferSize);
    }

    private synchronized long tryBorrow0(Type type) {
        LongArrayList cache = this.caches[type.ordinal()];
        if (cache == null || cache.isEmpty()) {
            return 0L;
        } else {
            return cache.removeLong(cache.size() - 1);
        }
    }

    public void returnBuffer(Type type, BufferEntry entry) {
        returnBuffer(type, entry.size(), entry.buffer());
    }

    public synchronized void returnBuffer(Type type, int size, long buffer) {
        LongArrayList cache = this.caches[type.ordinal()];
        if (cache == null || this.cacheBufferSizes[type.ordinal()] != size) {
            this.releaser.accept(buffer);
            return;
        }
        cache.add(buffer);
        while (cache.size() > MAX_CACHE_SIZE) {
            long removed = cache.removeLong(0);
            this.releaser.accept(removed);
        }
    }

    public synchronized void close() {
        for (LongArrayList cache : this.caches) {
            clearCache0(cache);
        }
        Arrays.fill(this.caches, null);
    }

    private void clearCache0(LongArrayList cache) {
        for (long buffer : cache) {
            this.releaser.accept(buffer);
        }
        cache.clear();
    }

    public record BufferEntry(long buffer, int size) {
    }

    public enum Type {
        ESTIMATE_SURFACE_HEIGHT_RX,
        FLATCACHE_RX,
        GEN_STAGE1_RW_DATA,
        GEN_RW_DATA,
        GEN_BLOCK_STATE_RX,
        GEN_BIOME_RX,
        GEN_BIOME_RW_DATA,
        GEN_BATCHING_RW_DATA,
        GEN_BATCHING_BLOCK_STATE_RX,
        GEN_BATCHING_BIOME_RX,
        ;
    }

}
