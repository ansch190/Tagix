package com.schwanitz.strategies.detection.context;

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
import java.util.*;

/**
 * FormatDetectionContext
 */
public class FormatDetectionContext {

    private static final Logger LOG = LoggerFactory.getLogger(FormatDetectionContext.class);
    private static final int BUFFER_SIZE = 4096;

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
     * Vereinfachte Detection: Keine Strategy-Tracking, kein komplexes switch-case
     */
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

    private String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return dotIndex == -1 ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }

    private List<TagFormat> getFormatsToCheck(ScanConfiguration config, String extension) {
        return switch (config.getMode()) {
            case FULL_SCAN -> FormatPriorityManager.getFullScanPriority();
            case COMFORT_SCAN -> FormatPriorityManager.getComfortScanPriority(extension);
            case CUSTOM_SCAN -> config.getCustomFormats();
        };
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

    private record FileBuffers(byte[] startBuffer, byte[] endBuffer) {}
}