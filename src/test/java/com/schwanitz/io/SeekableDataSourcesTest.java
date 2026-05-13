package com.schwanitz.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SeekableDataSourcesTest {

    @TempDir
    Path tempDir;

    @Test
    void byteArraySource_readWithinData() throws IOException {
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        SeekableDataSource src = SeekableDataSources.forBytes(data);
        byte[] buf = new byte[5];
        int read = src.read(3, buf, 0, 5);
        assertEquals(5, read);
        assertArrayEquals(new byte[]{3, 4, 5, 6, 7}, buf);
        src.close();
    }

    @Test
    void byteArraySource_readBeyondEnd() throws IOException {
        byte[] data = {0, 1, 2, 3};
        SeekableDataSource src = SeekableDataSources.forBytes(data);
        byte[] buf = new byte[10];
        int read = src.read(2, buf, 0, 10);
        assertEquals(2, read);
        assertArrayEquals(new byte[]{2, 3}, new byte[]{buf[0], buf[1]});
        src.close();
    }

    @Test
    void byteArraySource_readAtEnd() throws IOException {
        byte[] data = {0, 1, 2};
        SeekableDataSource src = SeekableDataSources.forBytes(data);
        byte[] buf = new byte[4];
        int read = src.read(3, buf, 0, 4);
        assertEquals(-1, read);
        src.close();
    }

    @Test
    void byteArraySource_length() throws IOException {
        byte[] data = new byte[42];
        SeekableDataSource src = SeekableDataSources.forBytes(data);
        assertEquals(42, src.length());
        src.close();
    }

    @Test
    void byteArraySource_readAll() throws IOException {
        byte[] data = {10, 20, 30, 40, 50};
        SeekableDataSource src = SeekableDataSources.forBytes(data);
        byte[] all = src.readAll();
        assertArrayEquals(data, all);
        assertNotSame(data, all);
        src.close();
    }

    @Test
    void byteArraySource_readWithBufOffset() throws IOException {
        byte[] data = {0, 1, 2, 3, 4, 5};
        SeekableDataSource src = SeekableDataSources.forBytes(data);
        byte[] buf = new byte[10];
        int read = src.read(1, buf, 3, 3);
        assertEquals(3, read);
        assertEquals(1, buf[3]);
        assertEquals(2, buf[4]);
        assertEquals(3, buf[5]);
        src.close();
    }

    @Test
    void byteArraySource_name() throws IOException {
        byte[] data = new byte[100];
        SeekableDataSource src = SeekableDataSources.forBytes(data);
        assertTrue(src.name().contains("100"));
        src.close();
    }

    @Test
    void pathSource_readAndLength() throws IOException {
        Path file = tempDir.resolve("test.bin");
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) i;
        Files.write(file, data);

        try (SeekableDataSource src = SeekableDataSources.forPath(file)) {
            assertEquals(256, src.length());
            byte[] buf = new byte[10];
            int read = src.read(100, buf, 0, 10);
            assertEquals(10, read);
            for (int i = 0; i < 10; i++) {
                assertEquals((byte) (100 + i), buf[i]);
            }
        }
    }

    @Test
    void pathSource_sequentialReads() throws IOException {
        Path file = tempDir.resolve("seq.bin");
        byte[] data = new byte[8192];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i & 0xFF);
        Files.write(file, data);

        try (SeekableDataSource src = SeekableDataSources.forPath(file)) {
            byte[] b1 = new byte[100];
            byte[] b2 = new byte[100];
            src.read(0, b1, 0, 100);
            src.read(100, b2, 0, 100);
            for (int i = 0; i < 100; i++) {
                assertEquals((byte) i, b1[i]);
                assertEquals((byte) (100 + i), b2[i]);
            }
        }
    }

    @Test
    void pathSource_readBeyondEnd() throws IOException {
        Path file = tempDir.resolve("small.bin");
        Files.write(file, new byte[]{1, 2, 3});

        try (SeekableDataSource src = SeekableDataSources.forPath(file)) {
            byte[] buf = new byte[10];
            int read = src.read(3, buf, 0, 10);
            assertEquals(-1, read);
        }
    }

    @Test
    void inputStreamTempFileSource_readAndDelete() throws IOException {
        byte[] data = new byte[500];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i & 0xFF);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        SeekableDataSource src = SeekableDataSources.forInputStream(bais, "bin");
        try {
            assertEquals(500, src.length());
            byte[] buf = new byte[10];
            int read = src.read(50, buf, 0, 10);
            assertEquals(10, read);
            for (int i = 0; i < 10; i++) {
                assertEquals((byte) ((50 + i) & 0xFF), buf[i]);
            }
        } finally {
            src.close();
        }
    }

    @Test
    void randomAccessFileSource_read() throws IOException {
        Path file = tempDir.resolve("raf.bin");
        byte[] data = new byte[100];
        for (int i = 0; i < 100; i++) data[i] = (byte) i;
        Files.write(file, data);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
             SeekableDataSource src = SeekableDataSources.forRandomAccessFile(raf)) {
            assertEquals(100, src.length());
            byte[] buf = new byte[10];
            int read = src.read(20, buf, 0, 10);
            assertEquals(10, read);
            for (int i = 0; i < 10; i++) {
                assertEquals((byte) (20 + i), buf[i]);
            }
        }
    }

    @Test
    void randomAccessFileSource_sequentialPosition() throws IOException {
        Path file = tempDir.resolve("raf2.bin");
        Files.write(file, new byte[1000]);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
             SeekableDataSource src = SeekableDataSources.forRandomAccessFile(raf)) {
            byte[] buf = new byte[10];
            src.read(0, buf, 0, 10);
            src.read(10, buf, 0, 10);
            src.read(20, buf, 0, 10);
        }
    }
}