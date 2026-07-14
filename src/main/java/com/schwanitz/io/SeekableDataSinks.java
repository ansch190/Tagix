package com.schwanitz.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Fabrikklasse und Implementierungen für {@link SeekableDataSink}.
 * <p>
 * Bietet bequeme statische Factory-Methoden zur Erstellung von Datenzielen
 * für verschiedene Schreibszenarien:
 * <ul>
 *   <li>{@link FileChannelSink} – Schreibt in eine temporäre Datei</li>
 *   <li>{@link TempFileSink} – Erstellt Temp-Datei mit Atomic-Replace-Unterstützung</li>
 *   <li>{@link ByteArraySink} – In-Memory-Puffer für Tests</li>
 * </ul>
 */
public final class SeekableDataSinks {

    private SeekableDataSinks() {}

    /**
     * Erstellt ein Datenziel, das direkt in die angegebene Datei schreibt.
     * <p>
     * Die Datei wird mit WRITE, CREATE und TRUNCATE_EXISTING geöffnet.
     * </p>
     *
     * @param path der Pfad zur Zieldatei
     * @return ein Datenziel, das in die Datei schreibt
     * @throws IOException wenn die Datei nicht geöffnet werden kann
     */
    public static SeekableDataSink forPath(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new FileChannelSink(channel, path.toString());
    }

    /**
     * Erstellt ein In-Memory-Datenziel.
     *
     * @return ein {@link ByteArraySink}
     */
    public static SeekableDataSink forBytes() {
        return new ByteArraySink();
    }

    /**
     * Erstellt ein In-Memory-Datenziel mit den angegebenen Anfangsdaten.
     *
     * @param data die Anfangsdaten
     * @return ein {@link ByteArraySink}
     */
    public static SeekableDataSink forBytes(byte[] data) {
        return new ByteArraySink(data);
    }

    /**
     * Erstellt ein temporäre Datei als Datenziel.
     * <p>
     * Die temporäre Datei wird im Standard-Temp-Verzeichnis erstellt.
     * Der optionale {@code suffix} Parameter wird als Dateisuffix verwendet.
     * </p>
     *
     * @param suffix optionales Dateisuffix (ohne Punkt), darf {@code null} sein
     * @return ein {@link TempFileSink} mit Atomic-Replace-Unterstützung
     * @throws IOException wenn die temporäre Datei nicht erstellt werden kann
     */
    public static TempFileSink forTempFile(String suffix) throws IOException {
        String actualSuffix = suffix != null && !suffix.isEmpty() ? "." + suffix : ".tmp";
        Path tempFile = Files.createTempFile("tagix-write", actualSuffix);
        FileChannel channel = FileChannel.open(tempFile,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new TempFileSink(channel, tempFile);
    }

    // ---- Implementation classes ----

    /**
     * Datenziel-Implementierung, die direkt in eine Datei über einen {@link FileChannel} schreibt.
     * <p>
     * Der {@link FileChannel} wird beim Schließen geschlossen.
     * </p>
     */
    public static final class FileChannelSink implements SeekableDataSink {

        private final FileChannel channel;
        private final String pathName;

        FileChannelSink(FileChannel channel, String pathName) {
            this.channel = channel;
            this.pathName = pathName;
        }

        @Override
        public void write(long offset, byte[] data, int off, int len) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(data, off, len);
            long totalWritten = 0;
            while (totalWritten < len) {
                int written = channel.write(buffer, offset + totalWritten);
                if (written == 0) {
                    throw new IOException("Konnte keine Bytes an Position " + (offset + totalWritten) + " schreiben");
                }
                totalWritten += written;
            }
        }

        @Override
        public void setLength(long newLength) throws IOException {
            channel.truncate(newLength);
        }

        @Override
        public long length() throws IOException {
            return channel.size();
        }

        @Override
        public void flush() throws IOException {
            channel.force(true);
        }

        @Override
        public String name() {
            return pathName;
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    /**
     * Temporäre Datei-Datenziel mit Atomic-Replace-Unterstützung.
     * <p>
     * Nach dem Schreiben kann {@link #commitTo(Path)} aufgerufen werden,
     * um die temporäre Datei atomar an die Zieldatei zu verschieben.
     * Beim Schließen wird die temporäre Datei gelöscht (sofern nicht gecommitet).
     * </p>
     */
    public static final class TempFileSink implements SeekableDataSink {

        private final FileChannel channel;
        private final Path tempFile;
        private boolean committed;

        TempFileSink(FileChannel channel, Path tempFile) {
            this.channel = channel;
            this.tempFile = tempFile;
            this.committed = false;
        }

        @Override
        public void write(long offset, byte[] data, int off, int len) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(data, off, len);
            long totalWritten = 0;
            while (totalWritten < len) {
                int written = channel.write(buffer, offset + totalWritten);
                if (written == 0) {
                    throw new IOException("Konnte keine Bytes an Position " + (offset + totalWritten) + " schreiben");
                }
                totalWritten += written;
            }
        }

        @Override
        public void setLength(long newLength) throws IOException {
            channel.truncate(newLength);
        }

        @Override
        public long length() throws IOException {
            return channel.size();
        }

        @Override
        public void flush() throws IOException {
            channel.force(true);
        }

        @Override
        public String name() {
            return tempFile.toString();
        }

        /**
         * Verschiebt die temporäre Datei atomar an die Zieldatei.
         * <p>
         * Die Zieldatei wird überschrieben. Nach dem Aufruf wird beim
         * {@link #close()}-Aufruf die temporäre Datei nicht mehr gelöscht.
         * </p>
         *
         * @param target der Pfad zur Zieldatei
         * @throws IOException wenn die Datei nicht verschoben werden kann
         */
        public void commitTo(Path target) throws IOException {
            flush();
            channel.close();
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            committed = true;
        }

        /**
         * Gibt den Pfad zur temporären Datei zurück.
         *
         * @return der Temp-Datei-Pfad
         */
        public Path getTempPath() {
            return tempFile;
        }

        @Override
        public void close() throws IOException {
            try {
                channel.close();
            } finally {
                if (!committed) {
                    Files.deleteIfExists(tempFile);
                }
            }
        }
    }
}
