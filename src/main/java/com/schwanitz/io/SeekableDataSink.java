package com.schwanitz.io;

import java.io.IOException;

/**
 * Abstraktionsschicht für positionierbare Datenziele zum Schreiben von Metadaten in Audiodateien.
 * <p>
 * Bietet wahlfreien Schreibzugriff analog zu {@link SeekableDataSource}, jedoch für Schreibvorgänge.
 * Implementierungen können auf Dateien, In-Memory-Puffern oder temporären Dateien basieren.
 * <p>
 * Die Typsignaturen sind symmetrisch zu {@link SeekableDataSource}, sodass beide
 * für die jeweilige Aufgabe verwendet werden können.
 * <p>
 * Beispiel:
 * <pre>
 * try (SeekableDataSink sink = SeekableDataSinks.forTempFile(".mp3")) {
 *     sink.write(0, headerBytes);
 *     sink.write(headerBytes.length, audioBytes);
 *     sink.flush();
 * }
 * </pre>
 */
public interface SeekableDataSink extends AutoCloseable {

    /**
     * Schreibt Bytes an die angegebene Position im Datenziel.
     *
     * @param offset die Position im Datenziel, an der geschrieben werden soll
     * @param data   das Byte-Array mit den zu schreibenden Daten
     * @param off    der Startindex im Quell-Array
     * @param len    die Anzahl der zu schreibenden Bytes
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    void write(long offset, byte[] data, int off, int len) throws IOException;

    /**
     * Convenience-Methode: Schreibt das gesamte Byte-Array an die angegebene Position.
     *
     * @param offset die Position im Datenziel
     * @param data   das zu schreibende Byte-Array
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    default void write(long offset, byte[] data) throws IOException {
        write(offset, data, 0, data.length);
    }

    /**
     * Setzt die Größe des Datenziels auf den angegebenen Wert.
     * <p>
     * Wenn {@code newLength} größer als die aktuelle Größe ist, werden
     * die zusätzlichen Bytes mit Nullen aufgefüllt. Wenn {@code newLength}
     * kleiner ist, wird die Datei gekürzt.
     * </p>
     *
     * @param newLength die neue Größe in Bytes
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    void setLength(long newLength) throws IOException;

    /**
     * Gibt die aktuelle Größe des Datenziels in Bytes zurück.
     *
     * @return die Größe in Bytes
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    long length() throws IOException;

    /**
     * Erzwingt das Schreiben aller gepufferten Daten in das Datenziel.
     *
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    void flush() throws IOException;

    /**
     * Gibt einen Anzeigenamen für dieses Datenziel zurück (z.B. Dateipfad oder "temp-file").
     *
     * @return ein menschenlesbarer Name
     */
    String name();

    /**
     * Schließt das Datenziel und gibt alle zugehörigen Ressourcen frei.
     *
     * @throws IOException bei Ein-/Ausgabefehlern beim Schließen
     */
    @Override
    void close() throws IOException;
}
