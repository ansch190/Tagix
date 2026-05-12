package com.schwanitz.others;

import com.schwanitz.interfaces.Metadata;
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

/**
 * Zentrale API für die Erkennung und das Parsen von Metadaten.
 * Kombiniert Tag-Detection mit Tag-Parsing in einer durchgängigen Pipeline.
 */
public class MetadataManager {

    private static final Logger Log = LoggerFactory.getLogger(MetadataManager.class);

    private final TagFormatDetector detector;

    public MetadataManager() {
        this.detector = new TagFormatDetector();
    }

    public MetadataManager(TagFormatDetector detector) {
        this.detector = detector;
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
        List<TagInfo> detectedTags = detector.fullScan(filePath);
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
                        Log.warn("Error parsing tag {} in {}: {}", tagInfo.getFormat(), filePath, e.getMessage());
                    }
                } else {
                    Log.debug("No parser available for format: {}", tagInfo.getFormat());
                }
            }
        } catch (IOException e) {
            Log.error("Cannot read file {}: {}", filePath, e.getMessage());
        }

        return metadataList;
    }
}
