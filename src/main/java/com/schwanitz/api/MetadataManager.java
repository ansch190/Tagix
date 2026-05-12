package com.schwanitz.api;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Zentrale API für die Erkennung und das Parsen von Metadaten.
 * Kombiniert Tag-Detection mit Tag-Parsing in einer durchgängigen Pipeline.
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

    /**
     * Liest alle Metadaten aus einer Datei (Full Scan).
     */
    public List<Metadata> readFromFile(String filePath) throws IOException {
        return readFromFile(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus einer Datei mit gegebener Konfiguration.
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
     * Liest alle Metadaten aus mehreren Dateien (Full Scan).
     */
    public Map<String, List<Metadata>> readFromFiles(List<String> filePaths) {
        return readFromFiles(filePaths, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus mehreren Dateien mit gegebener Konfiguration.
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
}