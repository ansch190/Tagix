package com.schwanitz.tagging;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.strategies.detection.context.FormatDetectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;

/**
 * Public API for tag format detection.
 * <p>
 * Provides multiple ways to detect tag formats in audio files:
 * <ul>
 *   <li>File path (String) - most common usage</li>
 *   <li>{@link Path} - NIO path support</li>
 *   <li>{@link SeekableDataSource} - abstraction over files, byte arrays, and streams</li>
 *   <li>{@code byte[]} - in-memory detection</li>
 *   <li>{@link InputStream} - streams (buffered to temp file internally)</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * TagFormatDetector detector = new TagFormatDetector();
 *
 * // From file path
 * List&lt;TagInfo&gt; tags = detector.fullScan("audio.mp3");
 *
 * // From byte array
 * List&lt;TagInfo&gt; tags = detector.fullScan(bytes);
 *
 * // From InputStream
 * try (SeekableDataSource source = SeekableDataSources.forInputStream(stream, "mp3")) {
 *     List&lt;TagInfo&gt; tags = detector.fullScan(source);
 * }
 * </pre>
 */
public class TagFormatDetector {

    private static final Logger LOG = LoggerFactory.getLogger(TagFormatDetector.class);
    private final FormatDetectionContext detectionContext;

    public TagFormatDetector() {
        this.detectionContext = new FormatDetectionContext();
    }

    public TagFormatDetector(FormatDetectionContext detectionContext) {
        this.detectionContext = Objects.requireNonNull(detectionContext, "detectionContext must not be null");
    }

    // ================================
    // FULL SCAN METHODS
    // ================================

    /**
     * Full Scan - single file by path
     */
    public List<TagInfo> fullScan(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        return detectionContext.detectTags(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Full Scan - single file by NIO Path
     */
    public List<TagInfo> fullScan(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        return detectionContext.detectTags(path.toString(), ScanConfiguration.fullScan());
    }

    /**
     * Full Scan - in-memory byte array
     */
    public List<TagInfo> fullScan(byte[] data) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            return fullScan(source);
        }
    }

    /**
     * Full Scan - seekable data source
     */
    public List<TagInfo> fullScan(SeekableDataSource source) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        return detectionContext.detectTags(source, ScanConfiguration.fullScan());
    }

    /**
     * Full Scan - InputStream (buffered to temporary file)
     *
     * @param inputStream the input stream to read from
     * @param extension   optional file extension hint (without dot), e.g. "mp3"
     */
    public List<TagInfo> fullScan(InputStream inputStream, String extension) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            return detectionContext.detectTags(source, ScanConfiguration.fullScan());
        }
    }

    /**
     * Full Scan - multiple files
     */
    public Map<String, List<TagInfo>> fullScan(List<String> filePaths) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.fullScan());
    }

    // ================================
    // COMFORT SCAN METHODS
    // ================================

    /**
     * Comfort Scan - single file by path
     */
    public List<TagInfo> comfortScan(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        return detectionContext.detectTags(filePath, ScanConfiguration.comfortScan());
    }

    /**
     * Comfort Scan - single file by NIO Path
     */
    public List<TagInfo> comfortScan(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        return detectionContext.detectTags(path.toString(), ScanConfiguration.comfortScan());
    }

    /**
     * Comfort Scan - in-memory byte array
     */
    public List<TagInfo> comfortScan(byte[] data) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            return comfortScan(source);
        }
    }

    /**
     * Comfort Scan - seekable data source
     */
    public List<TagInfo> comfortScan(SeekableDataSource source) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        return detectionContext.detectTags(source, ScanConfiguration.comfortScan());
    }

    /**
     * Comfort Scan - InputStream (buffered to temporary file)
     *
     * @param inputStream the input stream to read from
     * @param extension   optional file extension hint (without dot), e.g. "mp3"
     */
    public List<TagInfo> comfortScan(InputStream inputStream, String extension) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            return detectionContext.detectTags(source, ScanConfiguration.comfortScan());
        }
    }

    /**
     * Comfort Scan - multiple files
     */
    public Map<String, List<TagInfo>> comfortScan(List<String> filePaths) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.comfortScan());
    }

    // ================================
    // CUSTOM SCAN METHODS
    // ================================

    /**
     * Custom Scan - single file by path
     */
    public List<TagInfo> customScan(String filePath, TagFormat... formats) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        return detectionContext.detectTags(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Custom Scan - single file by NIO Path
     */
    public List<TagInfo> customScan(Path path, TagFormat... formats) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        return detectionContext.detectTags(path.toString(), ScanConfiguration.customScan(formats));
    }

    /**
     * Custom Scan - in-memory byte array
     */
    public List<TagInfo> customScan(byte[] data, TagFormat... formats) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            return customScan(source, formats);
        }
    }

    /**
     * Custom Scan - seekable data source
     */
    public List<TagInfo> customScan(SeekableDataSource source, TagFormat... formats) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        return detectionContext.detectTags(source, ScanConfiguration.customScan(formats));
    }

    /**
     * Custom Scan - InputStream (buffered to temporary file)
     *
     * @param inputStream the input stream to read from
     * @param extension   optional file extension hint (without dot), e.g. "mp3"
     * @param formats     the tag formats to detect
     */
    public List<TagInfo> customScan(InputStream inputStream, String extension, TagFormat... formats) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            return detectionContext.detectTags(source, ScanConfiguration.customScan(formats));
        }
    }

    /**
     * Custom Scan - multiple files
     */
    public Map<String, List<TagInfo>> customScan(List<String> filePaths, TagFormat... formats) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.customScan(formats));
    }

    // ================================
    // BATCH PROCESSING
    // ================================

    private Map<String, List<TagInfo>> detectTagFormats(List<String> filePaths, ScanConfiguration config) {
        Map<String, List<TagInfo>> results = new LinkedHashMap<>();
        List<String> validPaths = filePaths.stream()
                .filter(Objects::nonNull)
                .toList();

        if (validPaths.isEmpty()) {
            return results;
        }

        if (validPaths.size() == 1) {
            String filePath = validPaths.getFirst();
            try {
                results.put(filePath, detectionContext.detectTags(filePath, config));
            } catch (IOException e) {
                LOG.error("Error processing file {}: {}", filePath, e.getMessage());
                results.put(filePath, List.of());
            }
            return results;
        }

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
            Map<String, StructuredTaskScope.Subtask<List<TagInfo>>> tasks = new LinkedHashMap<>();
            for (String filePath : validPaths) {
                tasks.put(filePath, scope.fork(() -> {
                    try {
                        return detectionContext.detectTags(filePath, config);
                    } catch (IOException e) {
                        LOG.error("Error processing file {}: {}", filePath, e.getMessage());
                        return List.<TagInfo>of();
                    }
                }));
            }
            scope.join();

            for (var entry : tasks.entrySet()) {
                var subtask = entry.getValue();
                if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    results.put(entry.getKey(), subtask.get());
                } else {
                    LOG.error("Error processing file {}: {}", entry.getKey(), subtask.exception());
                    results.put(entry.getKey(), List.of());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Batch processing interrupted");
        }

        return results;
    }
}