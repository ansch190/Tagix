package com.schwanitz.io;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Abstraction over seekable data sources for tag detection and parsing.
 * <p>
 * Provides random access read capabilities similar to {@link RandomAccessFile}
 * but can be backed by different sources (files, in-memory buffers, etc.).
 * <p>
 * Usage with a file:
 * <pre>
 * try (SeekableDataSource source = SeekableDataSource.forPath(Path.of("audio.mp3"))) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 * <p>
 * Usage with an InputStream (buffers to temp file):
 * <pre>
 * try (SeekableDataSource source = SeekableDataSource.forInputStream(inputStream, "mp3")) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 * <p>
 * Usage with a byte array (in-memory):
 * <pre>
 * try (SeekableDataSource source = SeekableDataSource.forBytes(bytes)) {
 *     List&lt;TagInfo&gt; tags = detector.detectTags(source, config);
 * }
 * </pre>
 */
public interface SeekableDataSource extends AutoCloseable {

    /**
     * Returns the length of the data source in bytes.
     *
     * @return the length, or -1 if unknown
     * @throws IOException if an I/O error occurs
     */
    long length() throws IOException;

    /**
     * Reads up to {@code len} bytes from the data source starting at
     * position {@code offset}, into the byte array starting at {@code bufOff}.
     *
     * @param offset the file offset to start reading from
     * @param buf    the destination buffer
     * @param bufOff the start offset in the destination buffer
     * @param len    the maximum number of bytes to read
     * @return the actual number of bytes read, or -1 if end of file is reached
     * @throws IOException if an I/O error occurs
     */
    int read(long offset, byte[] buf, int bufOff, int len) throws IOException;

    /**
     * Reads the entire data source into a byte array.
     * For large files this allocates significant memory; use with caution.
     *
     * @return the data as a byte array
     * @throws IOException if an I/O error occurs
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
     * Returns a display name for this data source (e.g., file path or "memory buffer").
     *
     * @return a human-readable name
     */
    String name();

    @Override
    void close() throws IOException;
}