package com.schwanitz.tagging;

import com.schwanitz.strategies.detection.context.FormatDetectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagFormatDetector {
    private static final Logger Log = LoggerFactory.getLogger(TagFormatDetector.class);
    private static final int BUFFER_SIZE = 4096; // 4 KB Puffer für Anfang/Ende

    // In-Memory-Cache für TagInfo-Listen
    private static final Map<String, CachedTagInfo> tagInfoCache = new HashMap<>();
    private static final FormatDetectionContext detectionContext = new FormatDetectionContext();

    private static class CachedTagInfo {
        final List<TagInfo> tags;
        final long lastModified;
        final ScanConfiguration config;

        CachedTagInfo(List<TagInfo> tags, long lastModified, ScanConfiguration config) {
            this.tags = tags;
            this.lastModified = lastModified;
            this.config = config;
        }
    }

    /**
     * Neue Hauptmethode für konfigurierbare Tag-Erkennung
     *
     * @param filePath Pfad zur zu analysierenden Datei
     * @param config Scan-Konfiguration (Full/Comfort/Custom Scan)
     * @return Liste der erkannten Tag-Informationen
     * @throws IOException bei Dateizugriffsfehlern
     */
    public static List<TagInfo> detectTagFormats(String filePath, ScanConfiguration config) throws IOException {
        File f = new File(filePath);
        if (!f.exists() || !f.canRead()) {
            throw new IOException("Datei existiert nicht oder ist nicht lesbar: " + filePath);
        }

        // Cache-Key erstellen (Pfad + Konfiguration)
        String cacheKey = filePath + "_" + config.toString();

        // Prüfe Cache
        synchronized (tagInfoCache) {
            CachedTagInfo cached = tagInfoCache.get(cacheKey);
            if (cached != null && cached.lastModified == f.lastModified() &&
                    cached.config.equals(config)) {
                Log.debug("Using cached result for {}", filePath);
                return cached.tags;
            }
        }

        List<TagInfo> detectedTags;
        String extension = getFileExtension(filePath).toLowerCase();

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            // Puffer für Anfang und Ende der Datei
            byte[] startBuffer = new byte[BUFFER_SIZE];
            byte[] endBuffer = new byte[BUFFER_SIZE];

            file.seek(0);
            int startRead = file.read(startBuffer);

            long endPosition = Math.max(0, file.length() - BUFFER_SIZE);
            file.seek(endPosition);
            int endRead = file.read(endBuffer);

            // Neue konfigurierbare Detection verwenden
            detectedTags = detectionContext.detectTags(file, filePath, extension,
                    startBuffer, endBuffer, config);

            // Cache speichern
            synchronized (tagInfoCache) {
                tagInfoCache.put(cacheKey, new CachedTagInfo(detectedTags, f.lastModified(), config));
            }

            Log.info("Detected {} tags in {} using {}", detectedTags.size(), filePath, config.getMode());

        } catch (IOException e) {
            Log.error("Fehler beim Lesen der Datei " + filePath + ": " + e.getMessage());
            throw e;
        }

        return detectedTags;
    }

    /**
     * Convenience-Methode für Full Scan
     */
    public static List<TagInfo> detectTagFormatsFullScan(String filePath) throws IOException {
        return detectTagFormats(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Convenience-Methode für Comfort Scan
     */
    public static List<TagInfo> detectTagFormatsComfortScan(String filePath) throws IOException {
        return detectTagFormats(filePath, ScanConfiguration.comfortScan());
    }

    /**
     * Convenience-Methode für Custom Scan
     */
    public static List<TagInfo> detectTagFormatsCustomScan(String filePath, TagFormat... formats) throws IOException {
        return detectTagFormats(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Convenience-Methode für Custom Scan mit Liste
     */
    public static List<TagInfo> detectTagFormatsCustomScan(String filePath, List<TagFormat> formats) throws IOException {
        return detectTagFormats(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Batch-Verarbeitung: Mehrere Dateien mit derselben Konfiguration
     */
    public static Map<String, List<TagInfo>> detectTagFormats(List<String> filePaths, ScanConfiguration config) {
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

    /**
     * Cache-Management: Cache leeren
     */
    public static void clearCache() {
        synchronized (tagInfoCache) {
            tagInfoCache.clear();
            Log.debug("Tag info cache cleared");
        }
    }

    /**
     * Cache-Management: Einzelnen Eintrag entfernen
     */
    public static void removeCacheEntry(String filePath) {
        synchronized (tagInfoCache) {
            tagInfoCache.entrySet().removeIf(entry -> entry.getKey().startsWith(filePath + "_"));
            Log.debug("Cache entries for {} removed", filePath);
        }
    }

    /**
     * Cache-Statistiken abrufen
     */
    public static int getCacheSize() {
        synchronized (tagInfoCache) {
            return tagInfoCache.size();
        }
    }

    /**
     * Hilfsmethode: Dateiendung extrahieren
     */
    private static String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return dotIndex == -1 ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }
}