package com.schwanitz.strategies.detection.context;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.strategies.detection.factory.TagDetectionStrategyFactory;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.FormatPriorityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * FormatDetectionContext
 * <p>
 * Orchestrates tag format detection using a set of strategies.
 * Supports detection from file paths and {@link SeekableDataSource} instances
 * (which can be backed by files, byte arrays, or buffered streams).
 */
public class FormatDetectionContext {

    private static final Logger LOG = LoggerFactory.getLogger(FormatDetectionContext.class);
    private static final int BUFFER_SIZE = 4096;

    /**
     * Detect tags in a file by path.
     */
    public List<TagInfo> detectTags(String filePath, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(config, "config must not be null");

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            throw new IOException("Datei existiert nicht oder ist nicht lesbar: " + filePath);
        }

        if (file.length() == 0) {
            LOG.debug("Skipping empty file: {}", filePath);
            return List.of();
        }

        String extension = getFileExtension(filePath);
        List<TagFormat> formatsToCheck = getFormatsToCheck(config, extension);

        if (formatsToCheck == null || formatsToCheck.isEmpty()) {
            LOG.debug("No formats to check for file: {}", filePath);
            return List.of();
        }

        LOG.debug("Start Tag-Detection with Mode: {} for File: {}", config.getMode(), filePath);

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            FileBuffers buffers = readFileBuffers(raf);

            List<TagDetectionStrategy> strategies = TagDetectionStrategyFactory.getStrategiesForFormats(formatsToCheck);
            if (strategies.isEmpty()) {
                LOG.debug("No strategies found for requested formats");
                return List.of();
            }

            List<TagInfo> detectedTags = performDetection(strategies, formatsToCheck, raf, filePath, buffers);

            LOG.info("Detected {} tags in {} using {}", detectedTags.size(), filePath, config.getMode());
            return detectedTags;
        }
    }

    /**
     * Detect tags in a seekable data source (byte array, stream buffer, etc.).
     * <p>
     * For data sources backed by files, this uses the file directly.
     * For in-memory sources, this writes a temporary file for RandomAccessFile-based
     * detection strategies.
     */
    public List<TagInfo> detectTags(SeekableDataSource source, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(config, "config must not be null");

        long sourceLength = source.length();
        if (sourceLength == 0) {
            LOG.debug("Skipping empty data source: {}", source.name());
            return List.of();
        }

        String extension = getFileExtension(source.name());
        List<TagFormat> formatsToCheck = getFormatsToCheck(config, extension);

        if (formatsToCheck == null || formatsToCheck.isEmpty()) {
            LOG.debug("No formats to check for source: {}", source.name());
            return List.of();
        }

        LOG.debug("Start Tag-Detection with Mode: {} for source: {}", config.getMode(), source.name());

        FileBuffers buffers = readSourceBuffers(source);

        // For detection strategies that need RandomAccessFile, we create a temporary file
        // from the data source if it's not already file-backed
        try (RandomAccessFile raf = createRandomAccessFile(source)) {
            List<TagDetectionStrategy> strategies = TagDetectionStrategyFactory.getStrategiesForFormats(formatsToCheck);
            if (strategies.isEmpty()) {
                LOG.debug("No strategies found for requested formats");
                return List.of();
            }

            List<TagInfo> detectedTags = performDetection(strategies, formatsToCheck, raf, source.name(), buffers);

            LOG.info("Detected {} tags in {} using {}", detectedTags.size(), source.name(), config.getMode());
            return detectedTags;
        }
    }

    private List<TagInfo> performDetection(List<TagDetectionStrategy> strategies,
                                           List<TagFormat> requestedFormats,
                                           RandomAccessFile raf, String filePath,
                                           FileBuffers buffers) throws IOException {

        List<TagInfo> detectedTags = new ArrayList<>();

        LOG.debug("Using {} unique strategies for {} formats", strategies.size(), requestedFormats.size());

        for (TagDetectionStrategy strategy : strategies) {
            try {
                if (strategy.canDetect(buffers.startBuffer, buffers.endBuffer)) {
                    List<TagInfo> strategyTags = strategy.detectTags(raf, filePath,
                            buffers.startBuffer, buffers.endBuffer);

                    if (strategyTags != null) {
                        List<TagInfo> filteredTags = strategyTags.stream()
                                .filter(tag -> tag != null && requestedFormats.contains(tag.getFormat()))
                                .toList();

                        detectedTags.addAll(filteredTags);

                        LOG.debug("Strategy {} found {} matching tags",
                                strategy.getClass().getSimpleName(), filteredTags.size());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error in strategy {}: {}", strategy.getClass().getSimpleName(), e.getMessage());
            }
        }

        return detectedTags;
    }

    /**
     * Create a RandomAccessFile from a SeekableDataSource.
     * Writes to a temporary file for in-memory sources.
     */
    private RandomAccessFile createRandomAccessFile(SeekableDataSource source) throws IOException {
        byte[] data = source.readAll();
        Path tempFile = Files.createTempFile("tagix-detect", ".tmp");
        Files.write(tempFile, data);
        return new RandomAccessFile(tempFile.toFile(), "r");
    }

    private FileBuffers readFileBuffers(RandomAccessFile raf) throws IOException {
        byte[] startBuffer = new byte[BUFFER_SIZE];
        byte[] endBuffer = new byte[BUFFER_SIZE];

        raf.seek(0);
        int startRead = raf.read(startBuffer);

        long endPosition = Math.max(0, raf.length() - BUFFER_SIZE);
        raf.seek(endPosition);
        raf.read(endBuffer);

        if (raf.length() < BUFFER_SIZE) {
            byte[] actualStartBuffer = new byte[startRead];
            System.arraycopy(startBuffer, 0, actualStartBuffer, 0, startRead);
            startBuffer = actualStartBuffer;
            endBuffer = actualStartBuffer;
        }

        return new FileBuffers(startBuffer, endBuffer);
    }

    private FileBuffers readSourceBuffers(SeekableDataSource source) throws IOException {
        long sourceLength = source.length();
        int bufferSize = (int) Math.min(BUFFER_SIZE, sourceLength);

        byte[] startBuffer = new byte[bufferSize];
        int startRead = source.read(0, startBuffer, 0, bufferSize);
        if (startRead < bufferSize) {
            byte[] trimmed = new byte[Math.max(startRead, 0)];
            System.arraycopy(startBuffer, 0, trimmed, 0, Math.max(startRead, 0));
            startBuffer = trimmed;
            return new FileBuffers(startBuffer, startBuffer);
        }

        byte[] endBuffer;
        if (sourceLength <= BUFFER_SIZE) {
            endBuffer = startBuffer;
        } else {
            long endPosition = sourceLength - BUFFER_SIZE;
            endBuffer = new byte[BUFFER_SIZE];
            int endRead = source.read(endPosition, endBuffer, 0, BUFFER_SIZE);
            if (endRead < BUFFER_SIZE) {
                byte[] trimmed = new byte[Math.max(endRead, 0)];
                System.arraycopy(endBuffer, 0, trimmed, 0, Math.max(endRead, 0));
                endBuffer = trimmed;
            }
        }

        return new FileBuffers(startBuffer, endBuffer);
    }

    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex == -1) return "";
        // Strip query strings or fragments from URLs
        String ext = filePath.substring(dotIndex + 1).toLowerCase();
        int queryIdx = ext.indexOf('?');
        if (queryIdx >= 0) ext = ext.substring(0, queryIdx);
        int hashIdx = ext.indexOf('#');
        if (hashIdx >= 0) ext = ext.substring(0, hashIdx);
        return ext;
    }

    private List<TagFormat> getFormatsToCheck(ScanConfiguration config, String extension) {
        return switch (config.getMode()) {
            case FULL_SCAN -> FormatPriorityManager.getFullScanPriority();
            case COMFORT_SCAN -> FormatPriorityManager.getComfortScanPriority(extension);
            case CUSTOM_SCAN -> config.getCustomFormats();
        };
    }

    private record FileBuffers(byte[] startBuffer, byte[] endBuffer) {}
}