package com.schwanitz.strategies.detection.context;

import com.schwanitz.strategies.detection.*;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.FormatPriorityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FormatDetectionContext {

    private static final Logger Log = LoggerFactory.getLogger(FormatDetectionContext.class);

    private final List<FormatDetectionStrategy> strategies = new ArrayList<>();

    private final Map<TagFormat, FormatDetectionStrategy> formatToStrategyMap = new HashMap<>();

    public FormatDetectionContext() {
        initializeStrategies();
    }

    private void initializeStrategies() {
        // Alle verfügbaren Strategien registrieren
        registerStrategy(new MP3DetectionStrategy());
        registerStrategy(new WAVDetectionStrategy());
        registerStrategy(new OggFlacDetectionStrategy());
        registerStrategy(new MP4DetectionStrategy());
        registerStrategy(new AIFFDetectionStrategy());
    }

    private void registerStrategy(FormatDetectionStrategy strategy) {
        strategies.add(strategy);

        // Mapping für alle TagFormats erstellen, die diese Strategy unterstützen könnte
        for (TagFormat format : TagFormat.values()) {
            // Prüfe welche Formate die Strategy theoretisch unterstützt
            // basierend auf den bekannten Dateierweiterungen
            try {
                // Test mit dummy Daten ob die Strategy diesen Dateityp handhaben kann
                if (strategy.canDetect(new byte[0], new byte[0])) {
                    formatToStrategyMap.put(format, strategy);
                    break;
                }
            } catch (Exception e) {
                // Ignoriere Fehler bei der Initialisierung
            }
        }
    }

    /**
     * Neue Hauptmethode für konfigurierbare Tag-Erkennung
     */
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, String fileExtension,
                                    byte[] startBuffer, byte[] endBuffer, ScanConfiguration config) throws IOException {

        Log.debug("Starting tag detection with mode: {} for file: {}", config.getMode(), filePath);

        switch (config.getMode()) {
            case FULL_SCAN:
                return detectFullScan(file, filePath, fileExtension, startBuffer, endBuffer);

            case COMFORT_SCAN:
                return detectComfortScan(file, filePath, fileExtension, startBuffer, endBuffer);

            case CUSTOM_SCAN:
                return detectCustomScan(file, filePath, fileExtension, startBuffer, endBuffer, config.getCustomFormats());

            default:
                throw new IllegalArgumentException("Unknown scan mode: " + config.getMode());
        }
    }

    /**
     * Full Scan: Alle Formate nach globaler Priorität prüfen
     */
    private List<TagInfo> detectFullScan(RandomAccessFile file, String filePath, String fileExtension,
                                         byte[] startBuffer, byte[] endBuffer) throws IOException {

        List<TagInfo> allDetectedTags = new ArrayList<>();
        List<TagFormat> priorityOrder = FormatPriorityManager.getFullScanPriority();

        Log.debug("Full scan with {} formats in priority order", priorityOrder.size());

        for (TagFormat format : priorityOrder) {
            try {
                FormatDetectionStrategy strategy = formatToStrategyMap.get(format);
                if (strategy != null) {
                    List<TagInfo> detectedTags = strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    allDetectedTags.addAll(detectedTags);
                }
            } catch (Exception e) {
                Log.warn("Error detecting format {} in file {}: {}", format, filePath, e.getMessage());
            }
        }

        Log.debug("Full scan detected {} tags", allDetectedTags.size());
        return allDetectedTags;
    }

    /**
     * Comfort Scan: Nur wahrscheinliche Formate für Dateiendung prüfen
     */
    private List<TagInfo> detectComfortScan(RandomAccessFile file, String filePath, String fileExtension,
                                            byte[] startBuffer, byte[] endBuffer) throws IOException {

        List<TagInfo> allDetectedTags = new ArrayList<>();
        List<TagFormat> priorityOrder = FormatPriorityManager.getComfortScanPriority(fileExtension);

        Log.debug("Comfort scan for extension '{}' with {} formats", fileExtension, priorityOrder.size());

        for (TagFormat format : priorityOrder) {
            try {
                FormatDetectionStrategy strategy = formatToStrategyMap.get(format);
                if (strategy != null) {
                    List<TagInfo> detectedTags = strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    allDetectedTags.addAll(detectedTags);
                }
            } catch (Exception e) {
                Log.warn("Error detecting format {} in file {}: {}", format, filePath, e.getMessage());
            }
        }

        Log.debug("Comfort scan detected {} tags", allDetectedTags.size());
        return allDetectedTags;
    }

    /**
     * Custom Scan: Nur benutzerdefinierte Formate prüfen
     */
    private List<TagInfo> detectCustomScan(RandomAccessFile file, String filePath, String fileExtension,
                                           byte[] startBuffer, byte[] endBuffer, List<TagFormat> customFormats) throws IOException {

        List<TagInfo> allDetectedTags = new ArrayList<>();

        Log.debug("Custom scan with {} custom formats", customFormats.size());

        for (TagFormat format : customFormats) {
            try {
                FormatDetectionStrategy strategy = formatToStrategyMap.get(format);
                if (strategy != null) {
                    List<TagInfo> detectedTags = strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    allDetectedTags.addAll(detectedTags);
                } else {
                    Log.warn("No strategy found for custom format: {}", format);
                }
            } catch (Exception e) {
                Log.warn("Error detecting custom format {} in file {}: {}", format, filePath, e.getMessage());
            }
        }

        Log.debug("Custom scan detected {} tags", allDetectedTags.size());
        return allDetectedTags;
    }

}