package com.schwanitz.io;

import java.io.IOException;

/**
 * Abstraktionsschicht für seekable (positionierbare) Datenquellen zur Tag-Erkennung und -Analyse.
 * <p>
 * Bietet wahlfreien Lesezugriff, kann jedoch auf unterschiedlichen Quellen basieren
 * (Dateien, In-Memory-Puffer, Streams usw.).
 * <p>
 * Dateibasierte Implementierungen nutzen einen internen Puffer für effizienten
 * sequentiellen Zugriff: aufeinanderfolgende Lesezugriffe ohne explizites
 * {@code seek()} und Puffer-Treffer bei kleinen Leseoperationen.
 * <p>
 * Beispiel mit einer Datei:
 * <pre>
 * try (SeekableDataSource source = SeekableDataSources.forPath(Path.of("audio.mp3"))) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 * <p>
 * Beispiel mit einem InputStream (wird in temporäre Datei gepuffert):
 * <pre>
 * try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, "mp3")) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 * <p>
 * Beispiel mit einem Byte-Array (im Speicher):
 * <pre>
 * try (SeekableDataSource source = SeekableDataSources.forBytes(bytes)) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 */
public interface SeekableDataSource extends AutoCloseable {

    /**
     * Gibt die Länge der Datenquelle in Bytes zurück.
     *
     * @return die Länge in Bytes, oder -1 wenn unbekannt
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    long length() throws IOException;

    /**
     * Liest bis zu {@code len} Bytes ab der Position {@code offset} aus der Datenquelle
     * und schreibt sie in das Byte-Array ab dem Index {@code bufOff}.
     * <p>
     * Dateibasierte Implementierungen nutzen einen internen Puffer für effizienten
     * Zugriff: wenn die angeforderten Daten bereits im Puffer liegen, wird kein
     * I/O durchgeführt.
     *
     * @param offset die Dateiposition, ab der gelesen werden soll
     * @param buf    der Zielpuffer
     * @param bufOff der Startindex im Zielpuffer
     * @param len    die maximale Anzahl der zu lesenden Bytes
     * @return die tatsächliche Anzahl der gelesenen Bytes, oder -1 wenn das Ende der Datei erreicht ist
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    int read(long offset, byte[] buf, int bufOff, int len) throws IOException;

    /**
     * Convenience-Methode: Liest bis zu {@code buf.length} Bytes ab der Position {@code offset}.
     *
     * @param offset die Dateiposition, ab der gelesen werden soll
     * @param buf    der Zielpuffer
     * @return die tatsächliche Anzahl der gelesenen Bytes, oder -1 wenn das Ende erreicht ist
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    default int read(long offset, byte[] buf) throws IOException {
        return read(offset, buf, 0, buf.length);
    }

    /**
     * Liest exakt {@code buf.length} Bytes ab der Position {@code offset}.
     * Wirft eine {@link IOException}, wenn das Ende der Datenquelle vorzeitig erreicht wird.
     *
     * @param offset die Dateiposition, ab der gelesen werden soll
     * @param buf    der Zielpuffer, der vollständig gefüllt wird
     * @throws IOException bei Ein-/Ausgabefehlern oder unerwartetem Dateiende
     */
    default void readFully(long offset, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int read = read(offset + total, buf, total, buf.length - total);
            if (read < 0) {
                throw new IOException("Unexpected end of data source at offset " + (offset + total));
            }
            total += read;
        }
    }

    /**
     * Liest ein einzelnes Byte ab der angegebenen Position.
     *
     * @param offset die Dateiposition
     * @return das gelesene Byte (vorzeichenbehaftet, wie {@link java.io.RandomAccessFile#readByte()})
     * @throws IOException bei Ein-/Ausgabefehlern oder unerwartetem Dateiende
     */
    default byte readByte(long offset) throws IOException {
        byte[] buf = new byte[1];
        readFully(offset, buf);
        return buf[0];
    }

    /**
     * Prüft, ob das angegebene Offset innerhalb der Datenquelle liegt.
     *
     * @param offset das zu prüfende Offset
     * @return {@code true}, wenn das Offset gültig ist (0 <= offset < length())
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    default boolean isValidOffset(long offset) throws IOException {
        return offset >= 0 && offset < length();
    }

    /**
     * Liest die gesamte Datenquelle in ein Byte-Array ein.
     * Bei großen Dateien wird erheblicher Speicher allokiert; mit Vorsicht verwenden.
     *
     * @return die Daten als Byte-Array
     * @throws IOException bei Ein-/Ausgabefehlern oder wenn die Datenquelle zu groß ist
     */
    default byte[] readAll() throws IOException {
        long len = length();
        if (len < 0 || len > Integer.MAX_VALUE) {
            throw new IOException("Data source too large to read into byte array");
        }
        int intLen = (int) len;
        if (intLen > 64 * 1024 * 1024) { // 64 MB practical limit
            throw new IOException("Data source exceeds 64 MB read-all limit: " + intLen + " bytes");
        }
        byte[] data = new byte[intLen];
        int totalRead = 0;
        while (totalRead < intLen) {
            int read = read(totalRead, data, totalRead, intLen - totalRead);
            if (read <= 0) break;
            totalRead += read;
        }
        return data;
    }

    /**
     * Gibt einen Anzeigenamen für diese Datenquelle zurück (z. B. Dateipfad oder „memory buffer").
     *
     * @return ein menschenlesbarer Name
     */
    String name();

    /**
     * Schließt die Datenquelle und gibt alle zugehörigen Ressourcen frei.
     *
     * @throws IOException bei Ein-/Ausgabefehlern beim Schließen
     */
    @Override
    void close() throws IOException;
}