package com.schwanitz.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SourceWriterTest {

    @Test
    void write_byteArray() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            byte[] data = {1, 2, 3, 4, 5};
            writer.write(data);

            assertEquals(5, writer.getPosition());
            assertArrayEquals(data, sink.toByteArray());
        }
    }

    @Test
    void write_withOffsetAndLength() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            byte[] data = {10, 20, 30, 40, 50};
            writer.write(data, 1, 3);

            assertEquals(3, writer.getPosition());
            assertArrayEquals(new byte[]{20, 30, 40}, sink.toByteArray());
        }
    }

    @Test
    void writeByte() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            writer.writeByte(0x42);
            writer.writeByte(0xFF);

            assertEquals(2, writer.getPosition());
            assertArrayEquals(new byte[]{0x42, (byte) 0xFF}, sink.toByteArray());
        }
    }

    @Test
    void writeInt16BE() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            writer.writeInt16BE(0x0102);

            assertEquals(2, writer.getPosition());
            assertArrayEquals(new byte[]{0x01, 0x02}, sink.toByteArray());
        }
    }

    @Test
    void writeInt32BE() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            writer.writeInt32BE(0x01020304);

            assertEquals(4, writer.getPosition());
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, sink.toByteArray());
        }
    }

    @Test
    void writeInt32LE() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            writer.writeInt32LE(0x01020304);

            assertEquals(4, writer.getPosition());
            assertArrayEquals(new byte[]{0x04, 0x03, 0x02, 0x01}, sink.toByteArray());
        }
    }

    @Test
    void writeString_utf8() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            writer.writeString("ABC", java.nio.charset.StandardCharsets.UTF_8);

            assertEquals(3, writer.getPosition());
            assertArrayEquals(new byte[]{'A', 'B', 'C'}, sink.toByteArray());
        }
    }

    @Test
    void seek_andGetPosition() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            assertEquals(0, writer.getPosition());

            writer.write(new byte[]{1, 2, 3});
            assertEquals(3, writer.getPosition());

            writer.seek(0);
            assertEquals(0, writer.getPosition());

            writer.skipBytes(2);
            assertEquals(2, writer.getPosition());
        }
    }

    @Test
    void writeAtOffset() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 5)) {

            writer.write(new byte[]{10, 20, 30});

            assertEquals(8, writer.getPosition());
            assertEquals(8, sink.length());
            assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 10, 20, 30}, sink.toByteArray());
        }
    }

    @Test
    void length() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {

            assertEquals(0, writer.length());
            writer.write(new byte[]{1, 2, 3});
            assertEquals(3, writer.length());
        }
    }
}
