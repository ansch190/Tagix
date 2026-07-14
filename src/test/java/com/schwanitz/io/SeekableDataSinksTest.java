package com.schwanitz.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SeekableDataSinksTest {

    @TempDir
    Path tempDir;

    @Test
    void byteArraySink_writeAndRead() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16)) {
            assertEquals(0, sink.length());

            byte[] data = {1, 2, 3, 4, 5};
            sink.write(0, data);
            assertEquals(5, sink.length());

            byte[] result = sink.toByteArray();
            assertArrayEquals(data, result);
        }
    }

    @Test
    void byteArraySink_writeAtOffset() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16)) {
            byte[] data1 = {1, 2, 3};
            byte[] data2 = {4, 5};
            sink.write(0, data1);
            sink.write(3, data2);

            assertEquals(5, sink.length());
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, sink.toByteArray());
        }
    }

    @Test
    void byteArraySink_setLength() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16)) {
            sink.write(0, new byte[]{1, 2, 3, 4, 5});
            sink.setLength(3);
            assertEquals(3, sink.length());
            assertArrayEquals(new byte[]{1, 2, 3}, sink.toByteArray());

            sink.setLength(10);
            assertEquals(10, sink.length());
        }
    }

    @Test
    void byteArraySink_initialData() {
        byte[] data = {10, 20, 30};
        try (ByteArraySink sink = new ByteArraySink(data)) {
            assertEquals(3, sink.length());
            assertArrayEquals(data, sink.toByteArray());
        }
    }

    @Test
    void fileChannelSink_writeAndRead() throws IOException {
        Path target = tempDir.resolve("test.bin");
        byte[] data = {10, 20, 30, 40, 50};

        try (SeekableDataSink sink = SeekableDataSinks.forPath(target)) {
            sink.write(0, data);
            sink.flush();
        }

        assertTrue(Files.exists(target));
        assertArrayEquals(data, Files.readAllBytes(target));
    }

    @Test
    void fileChannelSink_setLength() throws IOException {
        Path target = tempDir.resolve("trunc.bin");

        try (SeekableDataSink sink = SeekableDataSinks.forPath(target)) {
            sink.write(0, new byte[]{1, 2, 3, 4, 5});
            sink.setLength(3);
            sink.flush();
        }

        assertEquals(3, Files.size(target));
    }

    @Test
    void tempFileSink_writeAndCommit() throws IOException {
        Path target = tempDir.resolve("committed.bin");
        byte[] data = {100, (byte) 200, 50};

        try (SeekableDataSinks.TempFileSink sink = SeekableDataSinks.forTempFile("bin")) {
            sink.write(0, data);
            sink.commitTo(target);
        }

        assertTrue(Files.exists(target));
        assertArrayEquals(data, Files.readAllBytes(target));
    }

    @Test
    void tempFileSink_closeDeletesTempFile() throws IOException {
        Path tempFile;
        try (SeekableDataSinks.TempFileSink sink = SeekableDataSinks.forTempFile("tmp")) {
            sink.write(0, new byte[]{1, 2, 3});
            tempFile = sink.getTempPath();
            assertTrue(Files.exists(tempFile));
        }
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void forBytes_createsByteArraySink() throws IOException {
        try (SeekableDataSink sink = SeekableDataSinks.forBytes()) {
            assertInstanceOf(ByteArraySink.class, sink);
            assertEquals(0, sink.length());
        }
    }

    @Test
    void forBytesWithData_createsPopulatedSink() throws IOException {
        byte[] data = {1, 2, 3};
        try (SeekableDataSink sink = SeekableDataSinks.forBytes(data)) {
            assertInstanceOf(ByteArraySink.class, sink);
            assertEquals(3, sink.length());
            assertArrayEquals(data, ((ByteArraySink) sink).toByteArray());
        }
    }
}
