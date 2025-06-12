package com.schwanitz.tagging;

import com.schwanitz.strategies.detection.context.FormatDetectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * Vereinfachter TagFormatDetector ohne Caching
 * <p>
 * Fokussiert sich ausschließlich auf die Kerntask: Tag-Format-Erkennung
 * Caching wird der Anwendungsschicht überlassen
 */
public class TagFormatDetector {

    private static final Logger Log = LoggerFactory.getLogger(TagFormatDetector.class);
    private static final int BUFFER_SIZE = 4096; // 4 KB Puffer
    private static final FormatDetectionContext detectionContext = new FormatDetectionContext();

    // ================================
    // FULL SCAN METHODS
    // ================================

    /**
     * Full Scan - einzelne Datei
     * prüft alle verfügbaren Tag-Formate in globaler Prioritätsreihenfolge
     */
    public static List<TagInfo> fullScan(String filePath) throws IOException {
        return detectTagFormats(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Full Scan - mehrere Dateien
     * prüft alle verfügbaren Tag-Formate für eine Liste von Dateien
     */
    public static Map<String, List<TagInfo>> fullScan(List<String> filePaths) {
        return detectTagFormats(filePaths, ScanConfiguration.fullScan());
    }

    // ================================
    // COMFORT SCAN METHODS
    // ================================

    /**
     * Comfort Scan - einzelne Datei
     * prüft nur wahrscheinliche Tag-Formate für die jeweilige Dateiendung
     */
    public static List<TagInfo> comfortScan(String filePath) throws IOException {
        return detectTagFormats(filePath, ScanConfiguration.comfortScan());
    }

    /**
     * Comfort Scan - mehrere Dateien
     * prüft nur wahrscheinliche Tag-Formate für die jeweiligen Dateiendungen
     */
    public static Map<String, List<TagInfo>> comfortScan(List<String> filePaths) {
        return detectTagFormats(filePaths, ScanConfiguration.comfortScan());
    }

    // ================================
    // CUSTOM SCAN METHODS
    // ================================

    /**
     * Custom Scan - einzelne Datei
     * prüft nur die angegebenen Tag-Formate
     */
    public static List<TagInfo> customScan(String filePath, TagFormat... formats) throws IOException {
        return detectTagFormats(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Custom Scan - mehrere Dateien
     * prüft nur die angegebenen Tag-Formate für eine Liste von Dateien
     */
    public static Map<String, List<TagInfo>> customScan(List<String> filePaths, TagFormat... formats) {
        return detectTagFormats(filePaths, ScanConfiguration.customScan(formats));
    }

    // ================================
    // CORE DETECTION METHODS
    // ================================

    /**
     * Hauptmethode für konfigurierbare Tag-Erkennung - einzelne Datei
     */
    private static List<TagInfo> detectTagFormats(String filePath, ScanConfiguration config) throws IOException {
        File file = new File(filePath);

        if (!file.exists() || !file.canRead()) {
            throw new IOException("Datei existiert nicht oder ist nicht lesbar: " + filePath);
        }

        String extension = getFileExtension(filePath).toLowerCase();

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            // Puffer für Anfang und Ende der Datei
            byte[] startBuffer = new byte[BUFFER_SIZE];
            byte[] endBuffer = new byte[BUFFER_SIZE];

            raf.seek(0);
            int startRead = raf.read(startBuffer);

            long endPosition = Math.max(0, raf.length() - BUFFER_SIZE);
            raf.seek(endPosition);
            int endRead = raf.read(endBuffer);

            // Tag-Erkennung durchführen
            List<TagInfo> detectedTags = detectionContext.detectTags(raf, filePath, extension,
                    startBuffer, endBuffer, config);

            Log.info("Detected {} tags in {} using {}", detectedTags.size(), filePath, config.getMode());
            return detectedTags;

        } catch (IOException e) {
            Log.error("Fehler beim Lesen der Datei {}: {}", filePath, e.getMessage());
            throw e;
        }
    }

    /**
     * Batch-Verarbeitung: Mehrere Dateien mit derselben Konfiguration
     */
    private static Map<String, List<TagInfo>> detectTagFormats(List<String> filePaths, ScanConfiguration config) {
        Map<String, List<TagInfo>> results = new HashMap<>();

        for (String filePath : filePaths) {
            try {
                List<TagInfo> tags = detectTagFormats(filePath, config);
                results.put(filePath, tags);
            } catch (IOException e) {
                Log.error("Error processing file {}: {}", filePath, e.getMessage());
                results.put(filePath, List.of()); // Leere Liste bei Fehler
            }
        }

        return results;
    }

    // ================================
    // UTILITY METHODS
    // ================================

    /**
     * Hilfsmethode: Dateiendung extrahieren
     */
    private static String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return dotIndex == -1 ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }
}