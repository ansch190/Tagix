package com.schwanitz.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Zentrale Hilfsklasse zum Lesen von Multi-Byte-Integer-Werten und Strings
 * aus Byte-Arrays, {@link SeekableDataSource}-Instanzen und {@link SourceReader}-Instanzen.
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

    private static final int READ_BUFFER_SIZE = 8192;

    // ================================
    // SeekableDataSource-basierte Methoden
    // ================================

    public static int readLittleEndianInt16(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[2];
        source.readFully(offset, bytes);
        return readLittleEndianInt16(bytes, 0);
    }

    public static int readLittleEndianUInt16(SeekableDataSource source, long offset) throws IOException {
        return readLittleEndianInt16(source, offset) & 0xFFFF;
    }

    public static int readLittleEndianInt32(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[4];
        source.readFully(offset, bytes);
        return readLittleEndianInt32(bytes, 0);
    }

    public static long readLittleEndianInt64(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[8];
        source.readFully(offset, bytes);
        return readLittleEndianInt64(bytes, 0);
    }

    public static long readLittleEndianUInt32(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[4];
        source.readFully(offset, bytes);
        return ((long) (bytes[0] & 0xFF)) |
                ((long) (bytes[1] & 0xFF) << 8) |
                ((long) (bytes[2] & 0xFF) << 16) |
                ((long) (bytes[3] & 0xFF) << 24);
    }

    public static int readBigEndianInt16(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[2];
        source.readFully(offset, bytes);
        return readBigEndianInt16(bytes, 0);
    }

    public static int readBigEndianInt32(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[4];
        source.readFully(offset, bytes);
        return readBigEndianInt32(bytes, 0);
    }

    public static long readBigEndianUInt32(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[4];
        source.readFully(offset, bytes);
        return ((long) (bytes[0] & 0xFF) << 24) |
                ((long) (bytes[1] & 0xFF) << 16) |
                ((long) (bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    public static long readBigEndianInt64(SeekableDataSource source, long offset) throws IOException {
        byte[] bytes = new byte[8];
        source.readFully(offset, bytes);
        return readBigEndianInt64(bytes, 0);
    }

    public static String readFixedString(SeekableDataSource source, long offset, int size) throws IOException {
        if (size <= 0) return "";
        byte[] data = new byte[size];
        source.readFully(offset, data);

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

    public static byte[] readBytes(SeekableDataSource source, long offset, int size) throws IOException {
        if (size <= 0) return new byte[0];

        byte[] data = new byte[size];
        int totalRead = 0;

        while (totalRead < size) {
            int toRead = Math.min(READ_BUFFER_SIZE, size - totalRead);
            int read = source.read(offset + totalRead, data, totalRead, toRead);
            if (read < 0) {
                throw new IOException("Unexpected end of data source at offset " + (offset + totalRead));
            }
            totalRead += read;
        }

        return data;
    }

    // ================================
    // SourceReader-basierte Methoden (sequentiell, position wird intern gehalten)
    // ================================

    public static int readLittleEndianInt16(SourceReader reader) throws IOException {
        byte[] bytes = new byte[2];
        reader.readFully(bytes);
        return readLittleEndianInt16(bytes, 0);
    }

    public static int readLittleEndianUInt16(SourceReader reader) throws IOException {
        return readLittleEndianInt16(reader) & 0xFFFF;
    }

    public static int readLittleEndianInt32(SourceReader reader) throws IOException {
        byte[] bytes = new byte[4];
        reader.readFully(bytes);
        return readLittleEndianInt32(bytes, 0);
    }

    public static long readLittleEndianInt64(SourceReader reader) throws IOException {
        byte[] bytes = new byte[8];
        reader.readFully(bytes);
        return readLittleEndianInt64(bytes, 0);
    }

    public static long readLittleEndianUInt32(SourceReader reader) throws IOException {
        byte[] bytes = new byte[4];
        reader.readFully(bytes);
        return ((long) (bytes[0] & 0xFF)) |
                ((long) (bytes[1] & 0xFF) << 8) |
                ((long) (bytes[2] & 0xFF) << 16) |
                ((long) (bytes[3] & 0xFF) << 24);
    }

    public static int readBigEndianInt16(SourceReader reader) throws IOException {
        byte[] bytes = new byte[2];
        reader.readFully(bytes);
        return readBigEndianInt16(bytes, 0);
    }

    public static int readBigEndianInt32(SourceReader reader) throws IOException {
        byte[] bytes = new byte[4];
        reader.readFully(bytes);
        return readBigEndianInt32(bytes, 0);
    }

    public static long readBigEndianUInt32(SourceReader reader) throws IOException {
        byte[] bytes = new byte[4];
        reader.readFully(bytes);
        return ((long) (bytes[0] & 0xFF) << 24) |
                ((long) (bytes[1] & 0xFF) << 16) |
                ((long) (bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    public static long readBigEndianInt64(SourceReader reader) throws IOException {
        byte[] bytes = new byte[8];
        reader.readFully(bytes);
        return readBigEndianInt64(bytes, 0);
    }

    public static String readFixedString(SourceReader reader, int size) throws IOException {
        if (size <= 0) return "";
        byte[] data = new byte[size];
        reader.readFully(data);

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

    public static byte[] readBytes(SourceReader reader, int size) throws IOException {
        if (size <= 0) return new byte[0];

        byte[] data = new byte[size];
        int totalRead = 0;

        while (totalRead < size) {
            int toRead = Math.min(READ_BUFFER_SIZE, size - totalRead);
            int read = reader.getSource().read(reader.getFilePointer(), data, totalRead, toRead);
            if (read < 0) {
                throw new IOException("Unexpected end of data source at position " + reader.getFilePointer());
            }
            totalRead += read;
        }
        reader.seek(reader.getFilePointer() + totalRead);

        return data;
    }
}
