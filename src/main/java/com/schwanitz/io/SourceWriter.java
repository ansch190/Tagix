package com.schwanitz.io;

import java.io.IOException;

/**
 * Adapter, der eine {@link SeekableDataSink} mit einer sequenziellen Schreib-API umschließt.
 * <p>
 * Analog zu {@link SourceReader} für Lesevorgänge, ermöglicht diese Klasse
 * sequenzielles Schreiben mit automatischer Positionsverwaltung.
 * </p>
 * <p>
 * Alle Schreibvorgänge aktualisieren die interne Position automatisch.
 * Die Klasse ist <strong>nicht</strong> thread-sicher und sollte pro Schreibvorgang
 * neu erzeugt werden.
 * </p>
 *
 * @see SeekableDataSink
 * @see SourceReader
 */
public final class SourceWriter implements AutoCloseable {

    private final SeekableDataSink sink;
    private long position;

    /**
     * Erzeugt einen neuen {@code SourceWriter} für das angegebene Datenziel.
     *
     * @param sink            das zugrundeliegende {@link SeekableDataSink}
     * @param initialPosition die Startposition für sequenzielles Schreiben
     */
    public SourceWriter(SeekableDataSink sink, long initialPosition) {
        this.sink = sink;
        this.position = initialPosition;
    }

    /**
     * Setzt die aktuelle Schreibposition.
     *
     * @param pos die neue Position
     */
    public void seek(long pos) {
        this.position = pos;
    }

    /**
     * Gibt die aktuelle Schreibposition zurück.
     *
     * @return die aktuelle Position
     */
    public long getPosition() {
        return position;
    }

    /**
     * Schreibt das gesamte Byte-Array an der aktuellen Position.
     *
     * @param data die zu schreibenden Daten
     * @throws IOException bei I/O-Fehlern
     */
    public void write(byte[] data) throws IOException {
        sink.write(position, data);
        position += data.length;
    }

    /**
     * Schreibt Bytes aus dem Array ab dem angegebenen Index an der aktuellen Position.
     *
     * @param data die Quelldaten
     * @param off  der Startindex im Quell-Array
     * @param len  die Anzahl der zu schreibenden Bytes
     * @throws IOException bei I/O-Fehlern
     */
    public void write(byte[] data, int off, int len) throws IOException {
        sink.write(position, data, off, len);
        position += len;
    }

    /**
     * Schreibt ein einzelnes Byte an der aktuellen Position.
     *
     * @param b das zu schreibende Byte
     * @throws IOException bei I/O-Fehlern
     */
    public void writeByte(int b) throws IOException {
        sink.write(position, new byte[]{(byte) b});
        position++;
    }

    /**
     * Schreibt einen 16-Bit-Ganzzahlwert in Big-Endian-Reihenfolge.
     *
     * @param value der zu schreibende Wert
     * @throws IOException bei I/O-Fehlern
     */
    public void writeInt16BE(int value) throws IOException {
        byte[] data = new byte[2];
        data[0] = (byte) ((value >> 8) & 0xFF);
        data[1] = (byte) (value & 0xFF);
        write(data);
    }

    /**
     * Schreibt einen 32-Bit-Ganzzahlwert in Big-Endian-Reihenfolge.
     *
     * @param value der zu schreibende Wert
     * @throws IOException bei I/O-Fehlern
     */
    public void writeInt32BE(int value) throws IOException {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        write(data);
    }

    /**
     * Schreibt einen 32-Bit-Ganzzahlwert in Little-Endian-Reihenfolge.
     *
     * @param value der zu schreibende Wert
     * @throws IOException bei I/O-Fehlern
     */
    public void writeInt32LE(int value) throws IOException {
        byte[] data = new byte[4];
        data[0] = (byte) (value & 0xFF);
        data[1] = (byte) ((value >> 8) & 0xFF);
        data[2] = (byte) ((value >> 16) & 0xFF);
        data[3] = (byte) ((value >> 24) & 0xFF);
        write(data);
    }

    /**
     * Schreibt einen String in der angegebenen Zeichenkodierung an der aktuellen Position.
     *
     * @param s       der zu schreibende String
     * @param charset die Zeichenkodierung
     * @throws IOException bei I/O-Fehlern
     */
    public void writeString(String s, java.nio.charset.Charset charset) throws IOException {
        byte[] bytes = s.getBytes(charset);
        write(bytes);
    }

    /**
     * Überspringt die angegebene Anzahl Bytes (setzt die Position voraus).
     *
     * @param n die Anzahl der zu überspringenden Bytes
     */
    public void skipBytes(int n) {
        this.position += n;
    }

    /**
     * Gibt die Länge des Datenziels zurück.
     *
     * @return die Länge in Bytes
     * @throws IOException bei I/O-Fehlern
     */
    public long length() throws IOException {
        return sink.length();
    }

    /**
     * Gibt das zugrundeliegende {@link SeekableDataSink} zurück.
     *
     * @return das Datenziel
     */
    public SeekableDataSink getSink() {
        return sink;
    }

    /**
     * Schließt das zugrundeliegende Datenziel.
     *
     * @throws IOException bei I/O-Fehlern
     */
    @Override
    public void close() throws IOException {
        sink.close();
    }
}
