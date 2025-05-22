package com.schwanitz.tagging;

import com.schwanitz.strategies.detection.context.FormatDetectionContext;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class TagFormatDetector {
    private static final Logger LOGGER = Logger.getLogger(TagFormatDetector.class.getName());
    private static final int BUFFER_SIZE = 4096; // 4 KB Puffer für Anfang/Ende

    // In-Memory-Cache für TagInfo-Listen
    private static final Map<String, CachedTagInfo> tagInfoCache = new HashMap<>();
    private static final FormatDetectionContext detectionContext = new FormatDetectionContext();

    private static class CachedTagInfo {
        final List<TagInfo> tags;
        final long lastModified;

        CachedTagInfo(List<TagInfo> tags, long lastModified) {
            this.tags = tags;
            this.lastModified = lastModified;
        }
    }

    public static List<TagInfo> detectTagFormats(String filePath, boolean fullSearch) throws IOException {
        File f = new File(filePath);
        if (!f.exists() || !f.canRead()) {
            throw new IOException("Datei existiert nicht oder ist nicht lesbar: " + filePath);
        }

        // Prüfe Cache
        synchronized (tagInfoCache) {
            CachedTagInfo cached = tagInfoCache.get(filePath);
            if (cached != null && cached.lastModified == f.lastModified()) {
                return cached.tags; // Kopie zurückgeben
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

            // Strategy Pattern verwenden
            if (fullSearch) {
                detectedTags = detectionContext.detectFullSearch(file, filePath, extension, startBuffer, endBuffer);
            } else {
                detectedTags = detectionContext.detectAllFormats(file, filePath, extension, startBuffer, endBuffer);
            }

            // Cache speichern
            synchronized (tagInfoCache) {
                tagInfoCache.put(filePath, new CachedTagInfo(detectedTags, f.lastModified()));
            }
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Lesen der Datei " + filePath + ": " + e.getMessage());
            throw e;
        }

        return detectedTags;
    }

    private static String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return dotIndex == -1 ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }
}