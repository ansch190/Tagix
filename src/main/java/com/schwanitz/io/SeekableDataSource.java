package com.schwanitz.io;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Abstraktionsschicht für seekable (positionierbare) Datenquellen zur Tag-Erkennung und - Analyse.
 * <p>
 * Bietet wahlfreien Lesezugriff ähnlich {@link RandomAccessFile}, kann jedoch auf
 * unterschiedlichen Quellen basieren (Dateien, In-Memory-Puffer, Streams usw.).
 * <p>
 * Beispiel mit einer Datei:
 * <pre>
 * try (SeekableDataSource source = SeekableDataSource.forPath(Path.of("audio.mp3"))) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 * <p>
 * Beispiel mit einem InputStream (wird in temporäre Datei gepuffert):
 * <pre>
 * try (SeekableDataSource source = SeekableDataSource.forInputStream(inputStream, "mp3")) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 * <p>
 * Beispiel mit einem Byte-Array (im Speicher):
 * <pre>
 * try (SeekableDataSource source = SeekableDataSource.forBytes(bytes)) {
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
        byte[] data = new byte[(int) len];
        int totalRead = 0;
        while (totalRead < len) {
            int read = read(totalRead, data, totalRead, (int) len - totalRead);
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