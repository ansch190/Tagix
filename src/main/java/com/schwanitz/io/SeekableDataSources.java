package com.schwanitz.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Fabrikklasse und Implementierungen für {@link SeekableDataSource}.
 * <p>
 * Bietet bequeme statische Factory-Methoden zur Erstellung von Datenquellen aus
 * verschiedenen Eingabetypen:
 * <ul>
 *   <li>{@link RandomAccessFile} – direkter Dateizugriff</li>
 *   <li>{@link Path} – öffnet eine Datei und verwaltet deren Lebenszyklus</li>
 *   <li>{@code byte[]} – In-Memory-Puffer</li>
 *   <li>{@link InputStream} – puffert in eine temporäre Datei</li>
 * </ul>
 */
public final class SeekableDataSources {

    /**
     * Privater Konstruktor, da diese Klasse nur statische Factory-Methoden bereitstellt.
     */
    private SeekableDataSources() {}

    /**
     * Erstellt eine Datenquelle, die auf einer {@link RandomAccessFile} basiert.
     * <p>
     * Der Aufrufer ist für das Schließen der {@code RandomAccessFile} verantwortlich;
     * das Schließen dieser Datenquelle schließt die zugrunde liegende Datei <em>nicht</em>.
     *
     * @param raf die RandomAccessFile, die als Datenquelle dient
     * @return eine Datenquelle, die auf der angegebenen Datei basiert
     */
    public static SeekableDataSource forRandomAccessFile(RandomAccessFile raf) {
        return new RandomAccessFileSource(raf);
    }

    /**
     * Erstellt eine Datenquelle, die auf einem Dateipfad basiert.
     * <p>
     * Die zurückgegebene Datenquelle öffnet und schließt ihre eigene {@link RandomAccessFile}.
     *
     * @param path der Pfad zur Datei
     * @return eine Datenquelle, die auf der Datei basiert
     * @throws IOException wenn die Datei nicht geöffnet werden kann
     */
    public static SeekableDataSource forPath(Path path) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        return new PathSource(raf, path.toString());
    }

    /**
     * Erstellt eine Datenquelle, die auf einem In-Memory-Byte-Array basiert.
     *
     * @param data das Byte-Array
     * @return eine Datenquelle, die auf dem Byte-Array basiert
     */
    public static SeekableDataSource forBytes(byte[] data) {
        return new ByteArraySource(data);
    }

    /**
     * Erstellt eine Datenquelle aus einem {@link InputStream}, indem der Stream in eine
     * temporäre Datei gepuffert wird.
     * <p>
     * Der optionale Parameter {@code extension} dient als Suffix für die temporäre Datei.
     * Die temporäre Datei wird beim Schließen der zurückgegebenen Datenquelle gelöscht.
     *
     * @param inputStream der Eingabestrom, der gepuffert werden soll
     * @param extension   optionaler Dateinamenssuffix (ohne Punkt), darf null sein
     * @return eine Datenquelle, die auf einer temporären Datei mit den Stream-Daten basiert
     * @throws IOException wenn der Stream nicht gelesen oder die temporäre Datei nicht erstellt werden kann
     */
    public static SeekableDataSource forInputStream(InputStream inputStream, String extension) throws IOException {
        String suffix = extension != null && !extension.isEmpty() ? "." + extension : ".tmp";
        Path tempFile = Files.createTempFile("tagix", suffix);
        try {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            throw e;
        }
        RandomAccessFile raf = new RandomAccessFile(tempFile.toFile(), "r");
        return new InputStreamTempFileSource(raf, tempFile);
    }

    // ---- Implementation classes ----

    /**
     * Datenquellen-Implementierung, die direkt auf einer {@link RandomAccessFile} operiert,
     * ohne deren Lebenszyklus zu verwalten.
     * <p>
     * Das Schließen dieser Datenquelle hat keinen Effekt; der Aufrufer ist für
     * das Schließen der zugrunde liegenden {@link RandomAccessFile} verantwortlich.
     */
    public static final class RandomAccessFileSource implements SeekableDataSource {
        /** Die zugrunde liegende {@link RandomAccessFile}, die nicht von dieser Klasse geschlossen wird. */
        private final RandomAccessFile raf;

        /**
         * Erstellt eine neue Datenquelle für die angegebene {@link RandomAccessFile}.
         *
         * @param raf die RandomAccessFile, die als Datenquelle dient
         */
        RandomAccessFileSource(RandomAccessFile raf) {
            this.raf = raf;
        }

        /**
         * {@inheritDoc}
         * Gibt die Länge der zugrunde liegenden Datei zurück.
         */
        @Override
        public long length() throws IOException {
            return raf.length();
        }

        /**
         * {@inheritDoc}
         * Positioniert den Dateizeiger und liest in den Puffer.
         */
        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            raf.seek(offset);
            return raf.read(buf, bufOff, len);
        }

        /**
         * {@inheritDoc}
         * Gibt die String-Repräsentation der zugrunde liegenden {@link RandomAccessFile} zurück.
         */
        @Override
        public String name() {
            return raf.toString();
        }

        /**
         * Hat keinen Effekt, da der Aufrufer den Lebenszyklus der {@link RandomAccessFile} verwaltet.
         */
        @Override
        public void close() {
            // Caller manages the RandomAccessFile lifecycle
        }
    }

    /**
     * Datenquellen-Implementierung, die eine Datei über einen Pfad öffnet und deren
     * Lebenszyklus eigenständig verwaltet.
     * <p>
     * Beim Schließen wird die zugrunde liegende {@link RandomAccessFile} geschlossen.
     */
    public static final class PathSource implements SeekableDataSource {
        /** Die geöffnete {@link RandomAccessFile} für den Lesezugriff. */
        private final RandomAccessFile raf;
        /** Der Dateipfad als String zur Anzeige. */
        private final String pathName;

        /**
         * Erstellt eine neue Datenquelle für die angegebene {@link RandomAccessFile}
         * mit dem zugehörigen Pfadnamen.
         *
         * @param raf      die geöffnete RandomAccessFile
         * @param pathName der Anzeigename (Dateipfad)
         */
        PathSource(RandomAccessFile raf, String pathName) {
            this.raf = raf;
            this.pathName = pathName;
        }

        /**
         * {@inheritDoc}
         * Gibt die Länge der zugrunde liegenden Datei zurück.
         */
        @Override
        public long length() throws IOException {
            return raf.length();
        }

        /**
         * {@inheritDoc}
         * Positioniert den Dateizeiger und liest in den Puffer.
         */
        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            raf.seek(offset);
            return raf.read(buf, bufOff, len);
        }

        /**
         * {@inheritDoc}
         * Gibt den Dateipfad zurück.
         */
        @Override
        public String name() {
            return pathName;
        }

        /**
         * Schließt die zugrunde liegende {@link RandomAccessFile}.
         *
         * @throws IOException bei Ein-/Ausgabefehlern beim Schließen
         */
        @Override
        public void close() throws IOException {
            raf.close();
        }
    }

    /**
     * Datenquellen-Implementierung, die auf einem In-Memory-Byte-Array basiert.
     * <p>
     * Ideal für kleine Datenmengen, die bereits vollständig im Speicher vorliegen.
     * Keine Ressourcen müssen geschlossen werden.
     */
    public static final class ByteArraySource implements SeekableDataSource {
        /** Das zugrunde liegende Byte-Array mit den Daten. */
        private final byte[] data;

        /**
         * Erstellt eine neue Datenquelle für das angegebene Byte-Array.
         *
         * @param data das Byte-Array, das als Datenquelle dient
         */
        ByteArraySource(byte[] data) {
            this.data = data;
        }

        /**
         * {@inheritDoc}
         * Gibt die Länge des Byte-Arrays zurück.
         */
        @Override
        public long length() {
            return data.length;
        }

        /**
         * {@inheritDoc}
         * Kopiert Daten aus dem Byte-Array in den Zielpuffer.
         * Gibt -1 zurück, wenn die Position hinter das Ende des Arrays zeigt.
         */
        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) {
            if (offset >= data.length) return -1;
            int bytesToRead = (int) Math.min(len, data.length - offset);
            System.arraycopy(data, (int) offset, buf, bufOff, bytesToRead);
            return bytesToRead;
        }

        /**
         * {@inheritDoc}
         * Gibt einen beschreibenden Namen inklusive Größe zurück.
         */
        @Override
        public String name() {
            return "memory-buffer (" + data.length + " bytes)";
        }

        /**
         * Hat keinen Effekt, da kein externer Ressource verwaltet wird.
         */
        @Override
        public void close() {
            // No resource to close for in-memory buffer
        }

        /**
         * Gibt eine Kopie des zugrunde liegenden Byte-Arrays zurück.
         *
         * @return eine Kopie der Daten als Byte-Array
         */
        @Override
        public byte[] readAll() {
            return data.clone();
        }
    }

    /**
     * Datenquellen-Implementierung, die einen {@link InputStream} in eine temporäre Datei
     * puffert und wahlfreien Lesezugriff darauf bietet.
     * <p>
     * Beim Schließen wird sowohl die {@link RandomAccessFile} geschlossen als auch die
     * temporäre Datei gelöscht.
     */
    public static final class InputStreamTempFileSource implements SeekableDataSource {
        /** Die {@link RandomAccessFile} für den Lesezugriff auf die temporäre Datei. */
        private final RandomAccessFile raf;
        /** Der Pfad zur temporären Datei, die beim Schließen gelöscht wird. */
        private final Path tempFile;

        /**
         * Erstellt eine neue Datenquelle für die angegebene {@link RandomAccessFile}
         * und die zugehörige temporäre Datei.
         *
         * @param raf      die geöffnete RandomAccessFile auf die temporäre Datei
         * @param tempFile der Pfad zur temporären Datei
         */
        InputStreamTempFileSource(RandomAccessFile raf, Path tempFile) {
            this.raf = raf;
            this.tempFile = tempFile;
        }

        /**
         * {@inheritDoc}
         * Gibt die Länge der temporären Datei zurück.
         */
        @Override
        public long length() throws IOException {
            return raf.length();
        }

        /**
         * {@inheritDoc}
         * Positioniert den Dateizeiger und liest in den Puffer.
         */
        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            raf.seek(offset);
            return raf.read(buf, bufOff, len);
        }

        /**
         * {@inheritDoc}
         * Gibt den Pfad zur temporären Datei zurück.
         */
        @Override
        public String name() {
            return tempFile.toString();
        }

        /**
         * Schließt die {@link RandomAccessFile} und löscht die temporäre Datei.
         *
         * @throws IOException bei Ein-/Ausgabefehlern beim Schließen oder Löschen
         */
        @Override
        public void close() throws IOException {
            try {
                raf.close();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
}