package com.schwanitz.tagging;

import com.schwanitz.strategies.detection.context.FormatDetectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Vereinfachter TagFormatDetector - nur noch Public API
 * <p>
 * Delegiert die gesamte Detection-Logik an FormatDetectionContext
 */
public class TagFormatDetector {

    private static final Logger Log = LoggerFactory.getLogger(TagFormatDetector.class);
    private static final FormatDetectionContext detectionContext = new FormatDetectionContext();

    // ================================
    // FULL SCAN METHODS
    // ================================

    /**
     * Full Scan - einzelne Datei
     */
    public static List<TagInfo> fullScan(String filePath) throws IOException {
        return detectionContext.detectTags(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Full Scan - mehrere Dateien
     */
    public static Map<String, List<TagInfo>> fullScan(List<String> filePaths) {
        return detectTagFormats(filePaths, ScanConfiguration.fullScan());
    }

    // ================================
    // COMFORT SCAN METHODS
    // ================================

    /**
     * Comfort Scan - einzelne Datei
     */
    public static List<TagInfo> comfortScan(String filePath) throws IOException {
        return detectionContext.detectTags(filePath, ScanConfiguration.comfortScan());
    }

    /**
     * Comfort Scan - mehrere Dateien
     */
    public static Map<String, List<TagInfo>> comfortScan(List<String> filePaths) {
        return detectTagFormats(filePaths, ScanConfiguration.comfortScan());
    }

    // ================================
    // CUSTOM SCAN METHODS
    // ================================

    /**
     * Custom Scan - einzelne Datei
     */
    public static List<TagInfo> customScan(String filePath, TagFormat... formats) throws IOException {
        return detectionContext.detectTags(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Custom Scan - mehrere Dateien
     */
    public static Map<String, List<TagInfo>> customScan(List<String> filePaths, TagFormat... formats) {
        return detectTagFormats(filePaths, ScanConfiguration.customScan(formats));
    }

    // ================================
    // BATCH PROCESSING
    // ================================

    /**
     * Batch-Verarbeitung: Mehrere Dateien mit derselben Konfiguration
     */
    private static Map<String, List<TagInfo>> detectTagFormats(List<String> filePaths, ScanConfiguration config) {
        Map<String, List<TagInfo>> results = new HashMap<>();

        for (String filePath : filePaths) {
            try {
                List<TagInfo> tags = detectionContext.detectTags(filePath, config);
                results.put(filePath, tags);
            } catch (IOException e) {
                Log.error("Error processing file {}: {}", filePath, e.getMessage());
                results.put(filePath, List.of()); // Leere Liste bei Fehler
            }
        }

        return results;
    }
}