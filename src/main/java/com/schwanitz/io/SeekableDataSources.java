package com.schwanitz.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

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
 * <p>
 * Die dateibasierten Implementierungen nutzen {@link FileChannel} mit positionalem
 * Lesen für optimale Performance: kein explizites {@code seek()} nötig, und bei
 * sequentiellem Zugriff wird der interne positionsbasierte Puffer genutzt, um
 * unnötige {@code seek()}-Aufrufe zu vermeiden.
 */
public final class SeekableDataSources {

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
     * Die zurückgegebene Datenquelle öffnet einen {@link FileChannel} für effizienten
     * positionalen Lesezugriff und schließt diesen beim {@code close()}.
     *
     * @param path der Pfad zur Datei
     * @return eine Datenquelle, die auf der Datei basiert
     * @throws IOException wenn die Datei nicht geöffnet werden kann
     */
    public static SeekableDataSource forPath(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        return new PathSource(channel, path.toString());
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
        FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.READ);
        return new InputStreamTempFileSource(channel, tempFile);
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
        private final RandomAccessFile raf;
        private long position = 0;

        RandomAccessFileSource(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public long length() throws IOException {
            return raf.length();
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            if (offset != position) {
                raf.seek(offset);
            }
            int bytesRead = raf.read(buf, bufOff, len);
            if (bytesRead > 0) {
                position = offset + bytesRead;
            }
            return bytesRead;
        }

        @Override
        public String name() {
            return raf.toString();
        }

        @Override
        public void close() {
        }
    }

    /**
     * Datenquellen-Implementierung, die eine Datei über einen {@link FileChannel} liest.
     * <p>
     * Verwendet positionales Lesen ({@link FileChannel#read(ByteBuffer, long)}),
     * sodass kein explizites {@code seek()} nötig ist. Der {@link FileChannel}
     * wird beim Schließen geschlossen.
     * <p>
     * Ein interner Puffer ermögliche effizienten sequentiellen Zugriff: wenn
     * eine Leseanforderung vollständig im Puffer liegt, wird kein I/O durchgeführt.
     */
    public static final class PathSource implements SeekableDataSource {
        private static final int BUFFER_SIZE = 8192;

        private final FileChannel channel;
        private final String pathName;
        private long fileLength = -1;
        private long bufferStart = -1;
        private byte[] buffer;
        private int bufferLength = 0;

        PathSource(FileChannel channel, String pathName) {
            this.channel = channel;
            this.pathName = pathName;
        }

        @Override
        public long length() throws IOException {
            if (fileLength < 0) {
                fileLength = channel.size();
            }
            return fileLength;
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            long fLen = length();
            if (offset >= fLen) return -1;
            int bytesRemaining = (int) Math.min(len, fLen - offset);
            if (bytesRemaining <= 0) return -1;

            int totalRead = 0;
            int bufPos = bufOff;
            int remaining = bytesRemaining;

            while (remaining > 0) {
                if (buffer != null && offset >= bufferStart && offset + remaining <= bufferStart + bufferLength) {
                    int copyLen = Math.min(remaining, bufferLength - (int)(offset - bufferStart));
                    System.arraycopy(buffer, (int)(offset - bufferStart), buf, bufPos, copyLen);
                    totalRead += copyLen;
                    offset += copyLen;
                    bufPos += copyLen;
                    remaining -= copyLen;
                } else {
                    refillBuffer(offset);
                    if (bufferLength == 0) break;
                    continue;
                }
            }

            return totalRead > 0 ? totalRead : -1;
        }

        private void refillBuffer(long offset) throws IOException {
            long fLen = length();
            long readStart = Math.max(0, offset - BUFFER_SIZE / 4);
            int toRead = (int) Math.min(BUFFER_SIZE, fLen - readStart);
            if (toRead <= 0) {
                bufferLength = 0;
                return;
            }
            if (buffer == null) {
                buffer = new byte[BUFFER_SIZE];
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer, 0, toRead);
            int bytesRead = channel.read(bb, readStart);
            if (bytesRead <= 0) {
                bufferLength = 0;
                return;
            }
            bufferStart = readStart;
            bufferLength = bytesRead;
        }

        @Override
        public String name() {
            return pathName;
        }

        @Override
        public void close() throws IOException {
            try {
                channel.close();
            } finally {
                buffer = null;
                bufferLength = 0;
            }
        }
    }

    /**
     * Datenquellen-Implementierung, die auf einem In-Memory-Byte-Array basiert.
     * <p>
     * Ideal für kleine Datenmengen, die bereits vollständig im Speicher vorliegen.
     * Keine Ressourcen müssen geschlossen werden.
     */
    public static final class ByteArraySource implements SeekableDataSource {
        private final byte[] data;

        ByteArraySource(byte[] data) {
            this.data = data;
        }

        @Override
        public long length() {
            return data.length;
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) {
            if (offset >= data.length) return -1;
            int bytesToRead = (int) Math.min(len, data.length - offset);
            System.arraycopy(data, (int) offset, buf, bufOff, bytesToRead);
            return bytesToRead;
        }

        @Override
        public String name() {
            return "memory-buffer (" + data.length + " bytes)";
        }

        @Override
        public void close() {
        }

        @Override
        public byte[] readAll() {
            return data.clone();
        }
    }

    /**
     * Datenquellen-Implementierung, die einen {@link InputStream} in eine temporäre Datei
     * gepuffert und wahlfreien Lesezugriff über einen {@link FileChannel} bietet.
     * <p>
     * Beim Schließen wird der {@link FileChannel} geschlossen und die temporäre Datei gelöscht.
     */
    public static final class InputStreamTempFileSource implements SeekableDataSource {
        private static final int BUFFER_SIZE = 8192;

        private final FileChannel channel;
        private final Path tempFile;
        private long fileLength = -1;
        private long bufferStart = -1;
        private byte[] buffer;
        private int bufferLength = 0;

        InputStreamTempFileSource(FileChannel channel, Path tempFile) {
            this.channel = channel;
            this.tempFile = tempFile;
        }

        @Override
        public long length() throws IOException {
            if (fileLength < 0) {
                fileLength = channel.size();
            }
            return fileLength;
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            long fLen = length();
            if (offset >= fLen) return -1;
            int bytesRemaining = (int) Math.min(len, fLen - offset);
            if (bytesRemaining <= 0) return -1;

            int totalRead = 0;
            int bufPos = bufOff;
            int remaining = bytesRemaining;

            while (remaining > 0) {
                if (buffer != null && offset >= bufferStart && offset + remaining <= bufferStart + bufferLength) {
                    int copyLen = Math.min(remaining, bufferLength - (int)(offset - bufferStart));
                    System.arraycopy(buffer, (int)(offset - bufferStart), buf, bufPos, copyLen);
                    totalRead += copyLen;
                    offset += copyLen;
                    bufPos += copyLen;
                    remaining -= copyLen;
                } else {
                    refillBuffer(offset);
                    if (bufferLength == 0) break;
                    continue;
                }
            }

            return totalRead > 0 ? totalRead : -1;
        }

        private void refillBuffer(long offset) throws IOException {
            long fLen = length();
            long readStart = Math.max(0, offset - BUFFER_SIZE / 4);
            int toRead = (int) Math.min(BUFFER_SIZE, fLen - readStart);
            if (toRead <= 0) {
                bufferLength = 0;
                return;
            }
            if (buffer == null) {
                buffer = new byte[BUFFER_SIZE];
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer, 0, toRead);
            int bytesRead = channel.read(bb, readStart);
            if (bytesRead <= 0) {
                bufferLength = 0;
                return;
            }
            bufferStart = readStart;
            bufferLength = bytesRead;
        }

        @Override
        public String name() {
            return tempFile.toString();
        }

        @Override
        public void close() throws IOException {
            try {
                channel.close();
            } finally {
                buffer = null;
                bufferLength = 0;
                Files.deleteIfExists(tempFile);
            }
        }
    }
}