package com.schwanitz.io;

import java.io.IOException;
import java.util.Arrays;

/**
 * In-Memory-Implementierung von {@link SeekableDataSink}.
 * <p>
 * Ideal für Unit-Tests und kleine Datenmengen. Der interne Puffer wächst
 * dynamisch bei Schreibvorgängen über die aktuelle Größe hinaus.
 * </p>
 */
public final class ByteArraySink implements SeekableDataSink {

    private byte[] buffer;
    private int size;

    /**
     * Erstellt einen neuen leeren ByteArraySink mit der angegebenen Anfangskapazität.
     *
     * @param initialCapacity die Anfangskapazität in Bytes
     */
    public ByteArraySink(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
        this.size = 0;
    }

    /**
     * Erstellt einen neuen leeren ByteArraySink mit einer Anfangskapazität von 4096 Bytes.
     */
    public ByteArraySink() {
        this(4096);
    }

    /**
     * Erstellt einen neuen ByteArraySink mit den angegebenen Initialdaten.
     *
     * @param data die Anfangsdaten
     */
    public ByteArraySink(byte[] data) {
        this.buffer = Arrays.copyOf(data, data.length);
        this.size = data.length;
    }

    @Override
    public void write(long offset, byte[] data, int off, int len) throws IOException {
        if (offset < 0 || off < 0 || len < 0 || off + len > data.length) {
            throw new IndexOutOfBoundsException("Ungültige Schreibparameter");
        }
        long endPos = offset + len;
        if (endPos > buffer.length) {
            buffer = Arrays.copyOf(buffer, (int) Math.max(endPos, buffer.length * 2));
        }
        System.arraycopy(data, off, buffer, (int) offset, len);
        if ((int) endPos > size) {
            size = (int) endPos;
        }
    }

    @Override
    public void setLength(long newLength) throws IOException {
        if (newLength < 0) {
            throw new IOException("Ungültige Länge: " + newLength);
        }
        if (newLength > buffer.length) {
            buffer = Arrays.copyOf(buffer, (int) newLength);
        }
        size = (int) newLength;
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public void flush() {
        // Kein Flush nötig für In-Memory
    }

    @Override
    public String name() {
        return "byte-array-sink (" + size + " bytes)";
    }

    @Override
    public void close() {
        buffer = null;
        size = 0;
    }

    /**
     * Gibt die aktuell gespeicherten Daten als Byte-Array zurück.
     * <p>
     * Das zurückgegebene Array enthält nur die tatsächlich geschriebenen Bytes
     * (bis zur aktuellen Größe), nicht den gesamten internen Puffer.
     * </p>
     *
     * @return eine Kopie der gespeicherten Daten
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, size);
    }
}
