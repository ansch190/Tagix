package com.schwanitz.io;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Zentrale Hilfsklasse zum Schreiben von Multi-Byte-Integer-Werten und Strings
 * in Byte-Arrays, {@link SeekableDataSink}-Instanzen und {@link SourceWriter}-Instanzen.
 * <p>
 * Gegenstück zu {@link BinaryDataReader}. Bietet statische Methoden für alle
 * gängigen Byte-Reihenfolgen (Big-Endian, Little-Endian, Synchsafe).
 * </p>
 */
public final class BinaryDataWriter {

    private BinaryDataWriter() {}

    // ================================
    // Big-Endian in byte[]
    // ================================

    /**
     * Schreibt einen 16-Bit-Wert in Big-Endian-Reihenfolge in ein Byte-Array.
     */
    public static void writeBigEndianInt16(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    /**
     * Schreibt einen 32-Bit-Wert in Big-Endian-Reihenfolge in ein Byte-Array.
     */
    public static void writeBigEndianInt32(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    // ================================
    // Little-Endian in byte[]
    // ================================

    /**
     * Schreibt einen 16-Bit-Wert in Little-Endian-Reihenfolge in ein Byte-Array.
     */
    public static void writeLittleEndianInt16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    /**
     * Schreibt einen 32-Bit-Wert in Little-Endian-Reihenfolge in ein Byte-Array.
     */
    public static void writeLittleEndianInt32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    // ================================
    // Synchsafe in byte[]
    // ================================

    /**
     * Schreibt einen 32-Bit-Wert als Synchsafe-Integer in ein Byte-Array.
     * <p>
     * Jedes Byte nutzt nur 7 Bits (MSB ist immer 0), was eine maximale Größe
     * von 256 MB ergibt.
     * </p>
     */
    public static void writeSynchsafeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 21) & 0x7F);
        data[offset + 1] = (byte) ((value >> 14) & 0x7F);
        data[offset + 2] = (byte) ((value >> 7) & 0x7F);
        data[offset + 3] = (byte) (value & 0x7F);
    }

    // ================================
    // In SeekableDataSink
    // ================================

    /**
     * Schreibt einen 32-Bit-Wert in Big-Endian-Reihenfolge an die angegebene Position.
     */
    public static void writeBigEndianInt32(SeekableDataSink sink, long offset, int value) throws IOException {
        byte[] data = new byte[4];
        writeBigEndianInt32(data, 0, value);
        sink.write(offset, data);
    }

    /**
     * Schreibt einen 32-Bit-Wert in Little-Endian-Reihenfolge an die angegebene Position.
     */
    public static void writeLittleEndianInt32(SeekableDataSink sink, long offset, int value) throws IOException {
        byte[] data = new byte[4];
        writeLittleEndianInt32(data, 0, value);
        sink.write(offset, data);
    }

    /**
     * Schreibt einen 16-Bit-Wert in Big-Endian-Reihenfolge an die angegebene Position.
     */
    public static void writeBigEndianInt16(SeekableDataSink sink, long offset, int value) throws IOException {
        byte[] data = new byte[2];
        writeBigEndianInt16(data, 0, value);
        sink.write(offset, data);
    }

    /**
     * Schreibt einen 16-Bit-Wert in Little-Endian-Reihenfolge an die angegebene Position.
     */
    public static void writeLittleEndianInt16(SeekableDataSink sink, long offset, int value) throws IOException {
        byte[] data = new byte[2];
        writeLittleEndianInt16(data, 0, value);
        sink.write(offset, data);
    }

    /**
     * Schreibt einen 32-Bit-Wert als Synchsafe-Integer an die angegebene Position.
     */
    public static void writeSynchsafeInt(SeekableDataSink sink, long offset, int value) throws IOException {
        byte[] data = new byte[4];
        writeSynchsafeInt(data, 0, value);
        sink.write(offset, data);
    }

    /**
     * Schreibt einen String in der angegebenen Zeichenkodierung an die angegebene Position.
     */
    public static void writeString(SeekableDataSink sink, long offset, String s, Charset charset) throws IOException {
        byte[] bytes = s.getBytes(charset);
        sink.write(offset, bytes);
    }

    // ================================
    // In SourceWriter (sequentiell)
    // ================================

    /**
     * Schreibt einen 32-Bit-Wert in Big-Endian-Reihenfolge sequentiell.
     */
    public static void writeBigEndianInt32(SourceWriter writer, int value) throws IOException {
        byte[] data = new byte[4];
        writeBigEndianInt32(data, 0, value);
        writer.write(data);
    }

    /**
     * Schreibt einen 32-Bit-Wert in Little-Endian-Reihenfolge sequentiell.
     */
    public static void writeLittleEndianInt32(SourceWriter writer, int value) throws IOException {
        byte[] data = new byte[4];
        writeLittleEndianInt32(data, 0, value);
        writer.write(data);
    }

    /**
     * Schreibt einen 16-Bit-Wert in Big-Endian-Reihenfolge sequentiell.
     */
    public static void writeBigEndianInt16(SourceWriter writer, int value) throws IOException {
        byte[] data = new byte[2];
        writeBigEndianInt16(data, 0, value);
        writer.write(data);
    }

    /**
     * Schreibt einen 16-Bit-Wert in Little-Endian-Reihenfolge sequentiell.
     */
    public static void writeLittleEndianInt16(SourceWriter writer, int value) throws IOException {
        byte[] data = new byte[2];
        writeLittleEndianInt16(data, 0, value);
        writer.write(data);
    }

    /**
     * Schreibt einen 32-Bit-Wert als Synchsafe-Integer sequentiell.
     */
    public static void writeSynchsafeInt(SourceWriter writer, int value) throws IOException {
        byte[] data = new byte[4];
        writeSynchsafeInt(data, 0, value);
        writer.write(data);
    }

    /**
     * Schreibt einen String in der angegebenen Zeichenkodierung sequentiell.
     */
    public static void writeString(SourceWriter writer, String s, Charset charset) throws IOException {
        byte[] bytes = s.getBytes(charset);
        writer.write(bytes);
    }
}
