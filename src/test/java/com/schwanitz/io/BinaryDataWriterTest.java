package com.schwanitz.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BinaryDataWriterTest {

    // ================================
    // byte[] Tests
    // ================================

    @Test
    void writeBigEndianInt16_inArray() {
        byte[] data = new byte[2];
        BinaryDataWriter.writeBigEndianInt16(data, 0, 0x0102);
        assertArrayEquals(new byte[]{0x01, 0x02}, data);
    }

    @Test
    void writeBigEndianInt32_inArray() {
        byte[] data = new byte[4];
        BinaryDataWriter.writeBigEndianInt32(data, 0, 0x01020304);
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, data);
    }

    @Test
    void writeLittleEndianInt16_inArray() {
        byte[] data = new byte[2];
        BinaryDataWriter.writeLittleEndianInt16(data, 0, 0x0102);
        assertArrayEquals(new byte[]{0x02, 0x01}, data);
    }

    @Test
    void writeLittleEndianInt32_inArray() {
        byte[] data = new byte[4];
        BinaryDataWriter.writeLittleEndianInt32(data, 0, 0x01020304);
        assertArrayEquals(new byte[]{0x04, 0x03, 0x02, 0x01}, data);
    }

    @Test
    void writeSynchsafeInt_inArray() {
        byte[] data = new byte[4];
        BinaryDataWriter.writeSynchsafeInt(data, 0, 15);
        assertEquals(0, data[0] & 0x7F);
        assertEquals(0, data[1] & 0x7F);
        assertEquals(0, data[2] & 0x7F);
        assertEquals(15, data[3] & 0x7F);
        assertEquals(0, data[0] & 0x80);
        assertEquals(0, data[1] & 0x80);
        assertEquals(0, data[2] & 0x80);
        assertEquals(0, data[3] & 0x80);
    }

    @Test
    void writeBigEndianInt16_withOffset() {
        byte[] data = new byte[4];
        BinaryDataWriter.writeBigEndianInt16(data, 2, 0x0102);
        assertArrayEquals(new byte[]{0x00, 0x00, 0x01, 0x02}, data);
    }

    @Test
    void writeBigEndianInt32_withOffset() {
        byte[] data = new byte[8];
        BinaryDataWriter.writeBigEndianInt32(data, 4, 0x01020304);
        assertEquals(0x01, data[4] & 0xFF);
        assertEquals(0x04, data[7] & 0xFF);
    }

    // ================================
    // ByteArraySink Tests
    // ================================

    @Test
    void writeBigEndianInt32_inSink() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16)) {
            BinaryDataWriter.writeBigEndianInt32(sink, 0, 0x01020304);
            byte[] result = sink.toByteArray();
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, result);
        }
    }

    @Test
    void writeLittleEndianInt32_inSink() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16)) {
            BinaryDataWriter.writeLittleEndianInt32(sink, 0, 0x01020304);
            byte[] result = sink.toByteArray();
            assertArrayEquals(new byte[]{0x04, 0x03, 0x02, 0x01}, result);
        }
    }

    @Test
    void writeSynchsafeInt_inSink() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16)) {
            BinaryDataWriter.writeSynchsafeInt(sink, 0, 127);
            byte[] result = sink.toByteArray();
            assertEquals(127, result[3] & 0x7F);
        }
    }

    @Test
    void writeString_inSink() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16)) {
            BinaryDataWriter.writeString(sink, 0, "Hi", StandardCharsets.US_ASCII);
            assertArrayEquals(new byte[]{'H', 'i'}, sink.toByteArray());
        }
    }

    // ================================
    // SourceWriter Tests
    // ================================

    @Test
    void writeBigEndianInt32_sequential() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {
            BinaryDataWriter.writeBigEndianInt32(writer, 0x01020304);
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, sink.toByteArray());
        }
    }

    @Test
    void writeLittleEndianInt32_sequential() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {
            BinaryDataWriter.writeLittleEndianInt32(writer, 0x01020304);
            assertArrayEquals(new byte[]{0x04, 0x03, 0x02, 0x01}, sink.toByteArray());
        }
    }

    @Test
    void writeSynchsafeInt_sequential() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {
            BinaryDataWriter.writeSynchsafeInt(writer, 15);
            byte[] result = sink.toByteArray();
            assertEquals(15, result[3] & 0x7F);
        }
    }

    @Test
    void writeString_sequential() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {
            BinaryDataWriter.writeString(writer, "Hello", StandardCharsets.UTF_8);
            assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), sink.toByteArray());
        }
    }

    @Test
    void multipleWrites_sequential() throws IOException {
        try (ByteArraySink sink = new ByteArraySink(16);
             SourceWriter writer = new SourceWriter(sink, 0)) {
            BinaryDataWriter.writeBigEndianInt16(writer, 0x0102);
            BinaryDataWriter.writeBigEndianInt32(writer, 0x03040506);
            assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}, sink.toByteArray());
        }
    }
}
