package com.schwanitz.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Factory methods and implementations for {@link SeekableDataSource}.
 * <p>
 * Provides convenient ways to create data sources from different input types:
 * <ul>
 *   <li>{@link RandomAccessFile} - direct file access</li>
 *   <li>{@link Path} - opens file and manages lifecycle</li>
 *   <li>{@code byte[]} - in-memory buffer</li>
 *   <li>{@link InputStream} - buffers to a temporary file</li>
 * </ul>
 */
public final class SeekableDataSources {

    private SeekableDataSources() {}

    /**
     * Creates a data source backed by a {@link RandomAccessFile}.
     * <p>
     * The caller is responsible for closing the RandomAccessFile;
     * closing this data source does <em>not</em> close the underlying file.
     *
     * @param raf the random access file
     * @return a data source backed by the given file
     */
    public static SeekableDataSource forRandomAccessFile(RandomAccessFile raf) {
        return new RandomAccessFileSource(raf);
    }

    /**
     * Creates a data source backed by a file path.
     * <p>
     * The returned data source opens and closes its own {@link RandomAccessFile}.
     *
     * @param path the path to the file
     * @return a data source backed by the file
     * @throws IOException if the file cannot be opened
     */
    public static SeekableDataSource forPath(Path path) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        return new PathSource(raf, path.toString());
    }

    /**
     * Creates a data source backed by an in-memory byte array.
     *
     * @param data the byte array
     * @return a data source backed by the byte array
     */
    public static SeekableDataSource forBytes(byte[] data) {
        return new ByteArraySource(data);
    }

    /**
     * Creates a data source from an {@link InputStream} by buffering it to a temporary file.
     * <p>
     * The optional {@code extension} hint is used for the temporary file suffix.
     * The temporary file is deleted when the returned data source is closed.
     *
     * @param inputStream the input stream to buffer
     * @param extension   optional file extension hint (without dot), may be null
     * @return a data source backed by a temporary file containing the stream data
     * @throws IOException if the stream cannot be read or the temp file cannot be created
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

    public static final class RandomAccessFileSource implements SeekableDataSource {
        private final RandomAccessFile raf;

        RandomAccessFileSource(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public long length() throws IOException {
            return raf.length();
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            raf.seek(offset);
            return raf.read(buf, bufOff, len);
        }

        @Override
        public String name() {
            return raf.toString();
        }

        @Override
        public void close() {
            // Caller manages the RandomAccessFile lifecycle
        }
    }

    public static final class PathSource implements SeekableDataSource {
        private final RandomAccessFile raf;
        private final String pathName;

        PathSource(RandomAccessFile raf, String pathName) {
            this.raf = raf;
            this.pathName = pathName;
        }

        @Override
        public long length() throws IOException {
            return raf.length();
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            raf.seek(offset);
            return raf.read(buf, bufOff, len);
        }

        @Override
        public String name() {
            return pathName;
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }

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
            // No resource to close for in-memory buffer
        }

        @Override
        public byte[] readAll() {
            return data.clone();
        }
    }

    public static final class InputStreamTempFileSource implements SeekableDataSource {
        private final RandomAccessFile raf;
        private final Path tempFile;

        InputStreamTempFileSource(RandomAccessFile raf, Path tempFile) {
            this.raf = raf;
            this.tempFile = tempFile;
        }

        @Override
        public long length() throws IOException {
            return raf.length();
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            raf.seek(offset);
            return raf.read(buf, bufOff, len);
        }

        @Override
        public String name() {
            return tempFile.toString();
        }

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