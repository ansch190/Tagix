package com.schwanitz.api;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Central API for detecting and parsing metadata in audio files.
 * <p>
 * Combines tag detection with tag parsing in a complete pipeline.
 * Supports multiple input sources: file paths, NIO paths, byte arrays,
 * input streams, and seekable data sources.
 */
public class MetadataManager {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataManager.class);

    private final TagFormatDetector detector;

    public MetadataManager() {
        this.detector = new TagFormatDetector();
    }

    public MetadataManager(TagFormatDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector must not be null");
    }

    // ================================
    // FILE PATH METHODS
    // ================================

    /**
     * Reads all metadata from a file (Full Scan).
     */
    public List<Metadata> readFromFile(String filePath) throws IOException {
        return readFromFile(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Reads metadata from a file with the given configuration.
     */
    public List<Metadata> readFromFile(String filePath, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(config, "config must not be null");
        List<TagInfo> detectedTags = switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(filePath);
            case COMFORT_SCAN -> detector.comfortScan(filePath);
            case CUSTOM_SCAN -> detector.customScan(filePath, config.getCustomFormats().toArray(new TagFormat[0]));
        };
        return parseTags(filePath, detectedTags);
    }

    /**
     * Reads all metadata from a file by NIO Path (Full Scan).
     */
    public List<Metadata> readFromFile(Path path) throws IOException {
        return readFromFile(path, ScanConfiguration.fullScan());
    }

    /**
     * Reads metadata from a file by NIO Path with the given configuration.
     */
    public List<Metadata> readFromFile(Path path, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(config, "config must not be null");
        List<TagInfo> detectedTags = switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(path);
            case COMFORT_SCAN -> detector.comfortScan(path);
            case CUSTOM_SCAN -> detector.customScan(path, config.getCustomFormats().toArray(new TagFormat[0]));
        };
        return parseTags(path.toString(), detectedTags);
    }

    // ================================
    // BYTE ARRAY METHODS
    // ================================

    /**
     * Reads all metadata from an in-memory byte array (Full Scan).
     */
    public List<Metadata> readFromBytes(byte[] data) throws IOException {
        return readFromBytes(data, ScanConfiguration.fullScan());
    }

    /**
     * Reads metadata from an in-memory byte array with the given configuration.
     */
    public List<Metadata> readFromBytes(byte[] data, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(config, "config must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            List<TagInfo> detectedTags = detectFromSource(source, config);
            return parseTagsFromSource(source, detectedTags);
        }
    }

    // ================================
    // INPUT STREAM METHODS
    // ================================

    /**
     * Reads all metadata from an InputStream (Full Scan).
     * <p>
     * The stream is buffered to a temporary file for random access.
     * The temporary file is deleted automatically after processing.
     *
     * @param inputStream the input stream to read from
     * @param extension   optional file extension hint (without dot), e.g. "mp3"
     */
    public List<Metadata> readFromInputStream(InputStream inputStream, String extension) throws IOException {
        return readFromInputStream(inputStream, extension, ScanConfiguration.fullScan());
    }

    /**
     * Reads metadata from an InputStream with the given configuration.
     * <p>
     * The stream is buffered to a temporary file for random access.
     * The temporary file is deleted automatically after processing.
     *
     * @param inputStream the input stream to read from
     * @param extension    optional file extension hint (without dot), e.g. "mp3"
     * @param config       the scan configuration
     */
    public List<Metadata> readFromInputStream(InputStream inputStream, String extension, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(config, "config must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            List<TagInfo> detectedTags = detectFromSource(source, config);
            return parseTagsFromSource(source, detectedTags);
        }
    }

    // ================================
    // SEEKABLE DATA SOURCE METHODS
    // ================================

    /**
     * Reads all metadata from a seekable data source (Full Scan).
     */
    public List<Metadata> readFromSource(SeekableDataSource source) throws IOException {
        return readFromSource(source, ScanConfiguration.fullScan());
    }

    /**
     * Reads metadata from a seekable data source with the given configuration.
     */
    public List<Metadata> readFromSource(SeekableDataSource source, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(config, "config must not be null");
        List<TagInfo> detectedTags = detectFromSource(source, config);
        return parseTagsFromSource(source, detectedTags);
    }

    // ================================
    // BATCH METHODS
    // ================================

    /**
     * Reads all metadata from multiple files (Full Scan).
     */
    public Map<String, List<Metadata>> readFromFiles(List<String> filePaths) {
        return readFromFiles(filePaths, ScanConfiguration.fullScan());
    }

    /**
     * Reads metadata from multiple files with the given configuration.
     */
    public Map<String, List<Metadata>> readFromFiles(List<String> filePaths, ScanConfiguration config) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Map<String, List<TagInfo>> detected = switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(filePaths);
            case COMFORT_SCAN -> detector.comfortScan(filePaths);
            case CUSTOM_SCAN -> detector.customScan(filePaths, config.getCustomFormats().toArray(new TagFormat[0]));
        };

        Map<String, List<Metadata>> results = new HashMap<>();
        for (Map.Entry<String, List<TagInfo>> entry : detected.entrySet()) {
            results.put(entry.getKey(), parseTags(entry.getKey(), entry.getValue()));
        }
        return results;
    }

    // ================================
    // INTERNAL METHODS
    // ================================

    private List<TagInfo> detectFromSource(SeekableDataSource source, ScanConfiguration config) throws IOException {
        return switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(source);
            case COMFORT_SCAN -> detector.comfortScan(source);
            case CUSTOM_SCAN -> detector.customScan(source, config.getCustomFormats().toArray(new TagFormat[0]));
        };
    }

    private List<Metadata> parseTags(String filePath, List<TagInfo> tagInfos) {
        List<Metadata> metadataList = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            for (TagInfo tagInfo : tagInfos) {
                TagParsingStrategy strategy = TagParsingStrategyFactory.getStrategyForFormat(tagInfo.getFormat());
                if (strategy != null) {
                    try {
                        Metadata metadata = strategy.parseTag(
                                tagInfo.getFormat(), raf, tagInfo.getOffset(), tagInfo.getSize());
                        metadataList.add(metadata);
                    } catch (IOException e) {
                        LOG.warn("Error parsing tag {} in {}: {}", tagInfo.getFormat(), filePath, e.getMessage());
                    }
                } else {
                    LOG.debug("No parser available for format: {}", tagInfo.getFormat());
                }
            }
        } catch (IOException e) {
            LOG.error("Cannot read file {}: {}", filePath, e.getMessage());
        }

        return metadataList;
    }

    private List<Metadata> parseTagsFromSource(SeekableDataSource source, List<TagInfo> tagInfos) {
        List<Metadata> metadataList = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(source.name(), "r")) {
            for (TagInfo tagInfo : tagInfos) {
                TagParsingStrategy strategy = TagParsingStrategyFactory.getStrategyForFormat(tagInfo.getFormat());
                if (strategy != null) {
                    try {
                        Metadata metadata = strategy.parseTag(
                                tagInfo.getFormat(), raf, tagInfo.getOffset(), tagInfo.getSize());
                        metadataList.add(metadata);
                    } catch (IOException e) {
                        LOG.warn("Error parsing tag {} in {}: {}", tagInfo.getFormat(), source.name(), e.getMessage());
                    }
                } else {
                    LOG.debug("No parser available for format: {}", tagInfo.getFormat());
                }
            }
        } catch (IOException e) {
            LOG.error("Cannot read source {}: {}", source.name(), e.getMessage());
        }

        return metadataList;
    }
}