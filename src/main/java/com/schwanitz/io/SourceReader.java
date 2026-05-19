package com.schwanitz.io;

import java.io.IOException;

/**
 * Adapter, der eine {@link SeekableDataSource} mit einer {@link java.io.RandomAccessFile}-ähnlichen
 * sequentiellen Lese-API umschließt.
 * <p>
 * Diese Klasse kapselt die aktuelle Leseposition und ermöglicht es, bestehenden Code,
 * der auf positionierbarem Lesen basiert, mit einer {@link SeekableDataSource} zu betreiben,
 * ohne die interne Logik der Aufrufer ändern zu müssen.
 * </p>
 * <p>
 * Alle Leseoperationen aktualisieren die interne Position automatisch. Die Klasse ist
 * <strong>nicht</strong> thread-sicher und sollte pro Parsing-Vorgang neu erzeugt werden.
 * </p>
 *
 * @see SeekableDataSource
 */
public final class SourceReader {

    private final SeekableDataSource source;
    private long position;

    /**
     * Erzeugt einen neuen {@code SourceReader} für die angegebene Datenquelle.
     *
     * @param source          die zugrundeliegende {@link SeekableDataSource}
     * @param initialPosition die Startposition für sequentielles Lesen
     */
    public SourceReader(SeekableDataSource source, long initialPosition) {
        this.source = source;
        this.position = initialPosition;
    }

    /**
     * Setzt die aktuelle Leseposition.
     *
     * @param pos die neue Position
     */
    public void seek(long pos) {
        this.position = pos;
    }

    /**
     * Gibt die aktuelle Leseposition zurück.
     *
     * @return die aktuelle Position
     */
    public long getFilePointer() {
        return position;
    }

    /**
     * Liest bis zu {@code buf.length} Bytes in den Puffer.
     *
     * @param buf der Zielpuffer
     * @return die Anzahl der gelesenen Bytes, oder -1 bei EOF
     * @throws IOException bei I/O-Fehlern
     */
    public int read(byte[] buf) throws IOException {
        int read = source.read(position, buf, 0, buf.length);
        if (read > 0) {
            position += read;
        }
        return read;
    }

    /**
     * Liest exakt {@code buf.length} Bytes. Wirft bei unerwartetem EOF.
     *
     * @param buf der Zielpuffer
     * @throws IOException bei I/O-Fehlern oder EOF
     */
    public void readFully(byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int read = source.read(position + total, buf, total, buf.length - total);
            if (read < 0) {
                throw new IOException("Unexpected end of data source at position " + (position + total));
            }
            total += read;
        }
        position += buf.length;
    }

    /**
     * Liest ein einzelnes Byte und gibt es als vorzeichenbehafteten Wert zurück.
     *
     * @return das gelesene Byte
     * @throws IOException bei I/O-Fehlern oder EOF
     */
    public byte readByte() throws IOException {
        byte[] buf = new byte[1];
        readFully(buf);
        return buf[0];
    }

    /**
     * Liest ein einzelnes Byte und gibt es als vorzeichenlosen int-Wert (0-255) zurück.
     * Gibt -1 bei EOF zurück.
     *
     * @return das gelesene Byte (0-255) oder -1 bei EOF
     * @throws IOException bei I/O-Fehlern
     */
    public int read() throws IOException {
        byte[] buf = new byte[1];
        int read = source.read(position, buf);
        if (read > 0) {
            position++;
            return buf[0] & 0xFF;
        }
        return -1;
    }

    /**
     * Überspringt die angegebene Anzahl Bytes.
     *
     * @param n die Anzahl der zu überspringenden Bytes
     * @return die tatsächlich übersprungene Anzahl (hier immer {@code n})
     */
    public int skipBytes(int n) {
        this.position += n;
        return n;
    }

    /**
     * Gibt die Länge der Datenquelle zurück.
     *
     * @return die Länge in Bytes
     * @throws IOException bei I/O-Fehlern
     */
    public long length() throws IOException {
        return source.length();
    }

    /**
     * Gibt die zugrundeliegende {@link SeekableDataSource} zurück.
     *
     * @return die Datenquelle
     */
    public SeekableDataSource getSource() {
        return source;
    }
}
