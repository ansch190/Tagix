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
     * Full Scan - einzelne Datei
     */
    public List<TagInfo> fullScan(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        return detectionContext.detectTags(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Full Scan - mehrere Dateien
     */
    public Map<String, List<TagInfo>> fullScan(List<String> filePaths) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.fullScan());
    }

    // ================================
    // COMFORT SCAN METHODS
    // ================================

    /**
     * Comfort Scan - einzelne Datei
     */
    public List<TagInfo> comfortScan(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        return detectionContext.detectTags(filePath, ScanConfiguration.comfortScan());
    }

    /**
     * Comfort Scan - mehrere Dateien
     */
    public Map<String, List<TagInfo>> comfortScan(List<String> filePaths) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.comfortScan());
    }

    // ================================
    // CUSTOM SCAN METHODS
    // ================================

    /**
     * Custom Scan - einzelne Datei
     */
    public List<TagInfo> customScan(String filePath, TagFormat... formats) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        return detectionContext.detectTags(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Custom Scan - mehrere Dateien
     */
    public Map<String, List<TagInfo>> customScan(List<String> filePaths, TagFormat... formats) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.customScan(formats));
    }

    // ================================
    // BATCH PROCESSING
    // ================================

    /**
     * Batch-Verarbeitung: Mehrere Dateien mit derselben Konfiguration
     */
    private Map<String, List<TagInfo>> detectTagFormats(List<String> filePaths, ScanConfiguration config) {
        Map<String, List<TagInfo>> results = new HashMap<>();

        for (String filePath : filePaths) {
            if (filePath == null) {
                LOG.warn("Skipping null file path in batch");
                continue;
            }
            try {
                List<TagInfo> tags = detectionContext.detectTags(filePath, config);
                results.put(filePath, tags);
            } catch (IOException e) {
                LOG.error("Error processing file {}: {}", filePath, e.getMessage());
                results.put(filePath, List.of());
            }
        }

        return results;
    }
}