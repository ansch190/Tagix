package com.schwanitz.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Zentrale Hilfsklasse zum Lesen von Multi-Byte-Integer-Werten und Strings
 * aus Byte-Arrays und {@link RandomAccessFile}-Instanzen.
 * <p>
 * Diese Klasse konsolidiert die zahlreichen duplizierten privaten
 * Byte-Lese-Methoden aus den Detection- und Parsing-Strategien in eine
 * einzige, wiederverwendbare Utility-Klasse mit ausschließlich statischen Methoden.
 * </p>
 */
public final class BinaryDataReader {

    private BinaryDataReader() {
        // Utility-Klasse – nicht instanziierbar
    }

    // ================================
    // Little-Endian aus byte[]
    // ================================

    public static int readLittleEndianInt16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    public static int readLittleEndianInt32(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    public static long readLittleEndianInt64(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF)) |
                ((long) (data[offset + 1] & 0xFF) << 8) |
                ((long) (data[offset + 2] & 0xFF) << 16) |
                ((long) (data[offset + 3] & 0xFF) << 24) |
                ((long) (data[offset + 4] & 0xFF) << 32) |
                ((long) (data[offset + 5] & 0xFF) << 40) |
                ((long) (data[offset + 6] & 0xFF) << 48) |
                ((long) (data[offset + 7] & 0xFF) << 56);
    }

    // ================================
    // Big-Endian aus byte[]
    // ================================

    public static int readBigEndianInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static int readBigEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
                ((data[offset + 1] & 0xFF) << 16) |
                ((data[offset + 2] & 0xFF) << 8) |
                (data[offset + 3] & 0xFF);
    }

    public static long readBigEndianInt64(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 56) |
                ((long) (data[offset + 1] & 0xFF) << 48) |
                ((long) (data[offset + 2] & 0xFF) << 40) |
                ((long) (data[offset + 3] & 0xFF) << 32) |
                ((long) (data[offset + 4] & 0xFF) << 24) |
                ((long) (data[offset + 5] & 0xFF) << 16) |
                ((long) (data[offset + 6] & 0xFF) << 8) |
                ((long) (data[offset + 7] & 0xFF));
    }

    // ================================
    // Little-Endian aus RandomAccessFile
    // ================================

    public static int readLittleEndianInt16(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8);
    }

    public static int readLittleEndianUInt16(RandomAccessFile file) throws IOException {
        return readLittleEndianInt16(file) & 0xFFFF;
    }

    public static int readLittleEndianInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return readLittleEndianInt32(bytes, 0);
    }

    public static long readLittleEndianInt64(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);
        return readLittleEndianInt64(bytes, 0);
    }

    public static long readLittleEndianUInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        int read = file.read(bytes);
        if (read != 4) {
            throw new IOException("Could not read 32-bit integer");
        }
        return ((long) (bytes[0] & 0xFF)) |
                ((long) (bytes[1] & 0xFF) << 8) |
                ((long) (bytes[2] & 0xFF) << 16) |
                ((long) (bytes[3] & 0xFF) << 24);
    }

    // ================================
    // Big-Endian aus RandomAccessFile
    // ================================

    public static int readBigEndianInt16(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    public static int readBigEndianInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return readBigEndianInt32(bytes, 0);
    }

    public static long readBigEndianUInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.read(bytes);
        return ((long) (bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    public static long readBigEndianInt64(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);
        return readBigEndianInt64(bytes, 0);
    }

    // ================================
    // String-Helfer
    // ================================

    public static String readFixedString(RandomAccessFile file, int size) throws IOException {
        byte[] data = new byte[size];
        file.read(data);

        int length = size;
        for (int i = 0; i < size; i++) {
            if (data[i] == 0) {
                length = i;
                break;
            }
        }

        if (length == 0) {
            return "";
        }

        return new String(data, 0, length, StandardCharsets.UTF_8).trim();
    }

    // ================================
    // Buffered byte[] read
    // ================================

    private static final int READ_BUFFER_SIZE = 8192;

    public static byte[] readBytes(RandomAccessFile file, int size) throws IOException {
        if (size <= 0) return new byte[0];

        byte[] data = new byte[size];
        int totalRead = 0;

        while (totalRead < size) {
            int toRead = Math.min(READ_BUFFER_SIZE, size - totalRead);
            int read = file.read(data, totalRead, toRead);
            if (read < 0) {
                throw new IOException("Unexpected end of file");
            }
            totalRead += read;
        }

        return data;
    }
}
