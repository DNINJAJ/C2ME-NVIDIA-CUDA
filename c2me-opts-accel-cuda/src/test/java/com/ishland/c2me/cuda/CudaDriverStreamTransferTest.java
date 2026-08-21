package com.ishland.c2me.cuda;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CudaDriverStreamTransferTest {
    @Test
    void roundTripsDirectBufferThroughTheSelectedStream() {
        CudaDriver driver;
        try {
            driver = CudaDriver.load();
        } catch (RuntimeException unavailable) {
            Assumptions.assumeTrue(false, "CUDA driver is unavailable: " + unavailable.getMessage());
            return;
        }

        long stream = driver.createStream();
        long device = driver.allocate(Integer.BYTES);
        ByteBuffer input = ByteBuffer.allocateDirect(Integer.BYTES).order(ByteOrder.nativeOrder());
        ByteBuffer output = ByteBuffer.allocateDirect(Integer.BYTES).order(ByteOrder.nativeOrder());
        try {
            input.putInt(0x1234ABCD).flip();
            driver.copyHostToDevice(stream, device, input);
            driver.copyDeviceToHost(stream, device, output);
            driver.synchronize(stream);
            assertEquals(0x1234ABCD, output.flip().getInt());
        } finally {
            driver.free(device);
            driver.destroyStream(stream);
        }
    }
}
