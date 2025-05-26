package com.schwanitz.examples;

import com.schwanitz.others.MetadataManager;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagFormatDetector;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.FormatPriorityManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Anwendungsbeispiele für die verschiedenen Scan-Modi
 */
public class ScanModeExamples {

    public static void main(String[] args) {
        // Beispiel-Dateipfade
        String mp3File = "beispiel.mp3";
        String wavFile = "beispiel.wav";
        String flacFile = "beispiel.flac";

        try {
            // =================
            // 1. COMFORT SCAN (Standard - empfohlen für normale Anwendung)
            // =================
            demonstrateComfortScan(mp3File);

            // =================
            // 2. FULL SCAN (Vollständige Analyse)
            // =================
            demonstrateFullScan(mp3File);

            // =================
            // 3. CUSTOM SCAN (Benutzerdefiniert)
            // =================
            demonstrateCustomScan(mp3File);

            // =================
            // 4. BATCH PROCESSING (Mehrere Dateien)
            // =================
            demonstrateBatchProcessing(Arrays.asList(mp3File, wavFile, flacFile));

            // =================
            // 5. METADATA MANAGER INTEGRATION
            // =================
            demonstrateMetadataManagerIntegration(mp3File);

            // =================
            // 6. PRIORITY MANAGEMENT
            // =================
            demonstratePriorityManagement();

        } catch (IOException e) {
            System.err.println("Fehler: " + e.getMessage());
        }
    }

    /**
     * Comfort Scan: Nur wahrscheinliche Tag-Formate für die Dateiendung
     */
    private static void demonstrateComfortScan(String filePath) throws IOException {
        System.out.println("=== COMFORT SCAN BEISPIEL ===");

        // Variante 1: Direkte Verwendung der Configuration
        ScanConfiguration config = ScanConfiguration.comfortScan();
        List<TagInfo> tags = TagFormatDetector.detectTagFormats(filePath, config);

        System.out.println("Comfort Scan für " + filePath + ":");
        System.out.println("Gefundene Tags: " + tags.size());

        for (TagInfo tag : tags) {
            System.out.printf("  - %s: Offset=%d, Size=%d%n",
                    tag.getFormat(), tag.getOffset(), tag.getSize());
        }

        // Variante 2: Convenience-Methode
        List<TagInfo> tagsConvenience = TagFormatDetector.detectTagFormatsComfortScan(filePath);
        System.out.println("Comfort Scan (Convenience): " + tagsConvenience.size() + " Tags\n");
    }

    /**
     * Full Scan: Alle Tag-Formate werden geprüft
     */
    private static void demonstrateFullScan(String filePath) throws IOException {
        System.out.println("=== FULL SCAN BEISPIEL ===");

        // Variante 1: Configuration
        ScanConfiguration config = ScanConfiguration.fullScan();
        List<TagInfo> tags = TagFormatDetector.detectTagFormats(filePath, config);

        System.out.println("Full Scan für " + filePath + ":");
        System.out.println("Gefundene Tags: " + tags.size());

        // Zeige alle gefundenen Formate
        for (TagInfo tag : tags) {
            System.out.printf("  - %s: Offset=%d, Size=%d%n",
                    tag.getFormat(), tag.getOffset(), tag.getSize());
        }

        // Variante 2: Convenience-Methode
        List<TagInfo> tagsConvenience = TagFormatDetector.detectTagFormatsFullScan(filePath);
        System.out.println("Full Scan (Convenience): " + tagsConvenience.size() + " Tags\n");
    }

    /**
     * Custom Scan: Nur spezifische Tag-Formate prüfen
     */
    private static void demonstrateCustomScan(String filePath) throws IOException {
        System.out.println("=== CUSTOM SCAN BEISPIELE ===");

        // Beispiel 1: Nur ID3-Formate
        System.out.println("1. Nur ID3-Formate:");
        ScanConfiguration id3Only = ScanConfiguration.customScan(
                TagFormat.ID3V2_3, TagFormat.ID3V2_4, TagFormat.ID3V1, TagFormat.ID3V1_1
        );
        List<TagInfo> id3Tags = TagFormatDetector.detectTagFormats(filePath, id3Only);
        System.out.println("ID3-Tags gefunden: " + id3Tags.size());

        // Beispiel 2: Nur moderne Formate
        System.out.println("2. Nur moderne Formate:");
        ScanConfiguration modernFormats = ScanConfiguration.customScan(
                TagFormat.ID3V2_4, TagFormat.VORBIS_COMMENT, TagFormat.MP4, TagFormat.APEV2
        );
        List<TagInfo> modernTags = TagFormatDetector.detectTagFormats(filePath, modernFormats);
        System.out.println("Moderne Tags gefunden: " + modernTags.size());

        // Beispiel 3: Spezifische Formate mit Convenience-Methoden
        System.out.println("3. Convenience-Methoden:");
        List<TagInfo> customTags1 = TagFormatDetector.detectTagFormatsCustomScan(
                filePath, TagFormat.ID3V2_3, TagFormat.APEV2
        );
        System.out.println("Custom Scan (Array): " + customTags1.size() + " Tags");

        List<TagInfo> customTags2 = TagFormatDetector.detectTagFormatsCustomScan(
                filePath, Arrays.asList(TagFormat.ID3V2_4, TagFormat.VORBIS_COMMENT)
        );
        System.out.println("Custom Scan (Liste): " + customTags2.size() + " Tags\n");
    }

    /**
     * Batch Processing: Mehrere Dateien gleichzeitig verarbeiten
     */
    private static void demonstrateBatchProcessing(List<String> filePaths) {
        System.out.println("=== BATCH PROCESSING BEISPIEL ===");

        // Alle Dateien mit Comfort Scan
        ScanConfiguration config = ScanConfiguration.comfortScan();
        Map<String, List<TagInfo>> results = TagFormatDetector.detectTagFormats(filePaths, config);

        System.out.println("Batch-Verarbeitung von " + filePaths.size() + " Dateien:");

        for (Map.Entry<String, List<TagInfo>> entry : results.entrySet()) {
            String fileName = entry.getKey();
            List<TagInfo> tags = entry.getValue();
            System.out.printf("  %s: %d Tag(s)%n", fileName, tags.size());

            for (TagInfo tag : tags) {
                System.out.printf("    - %s%n", tag.getFormat());
            }
        }
        System.out.println();
    }

    /**
     * MetadataManager Integration
     */
    private static void demonstrateMetadataManagerIntegration(String filePath) throws IOException {
        System.out.println("=== METADATA MANAGER INTEGRATION ===");

        MetadataManager manager = new MetadataManager();

        // Verschiedene Scan-Modi mit MetadataManager

        // 1. Standard (Comfort Scan)
        manager.readFromFile(filePath);
        System.out.println("Nach Comfort Scan: " + manager.getMetadataCount() + " Metadaten-Container");

        // 2. Full Scan
        manager.clearMetadata();
        manager.readFromFileFullScan(filePath);
        System.out.println("Nach Full Scan: " + manager.getMetadataCount() + " Metadaten-Container");

        // 3. Custom Scan
        manager.clearMetadata();
        manager.readFromFileCustomScan(filePath, TagFormat.ID3V2_3, TagFormat.ID3V1);
        System.out.println("Nach Custom Scan: " + manager.getMetadataCount() + " Metadaten-Container");

        // 4. Batch-Verarbeitung mit MetadataManager
        manager.clearMetadata();
        List<String> files = Arrays.asList(filePath); // Normalerweise mehrere Dateien
        Map<String, Integer> batchResults = manager.readFromFiles(files, ScanConfiguration.comfortScan());

        System.out.println("Batch-Ergebnisse:");
        for (Map.Entry<String, Integer> entry : batchResults.entrySet()) {
            System.out.printf("  %s: %d Metadaten hinzugefügt%n", entry.getKey(), entry.getValue());
        }

        // Summary ausgeben
        manager.printMetadataSummary();
        System.out.println();
    }

    /**
     * Prioritäts-Management und -Anpassung
     */
    private static void demonstratePriorityManagement() {
        System.out.println("=== PRIORITY MANAGEMENT ===");

        // Aktuelle Prioritäten anzeigen
        System.out.println("Full Scan Prioritäten:");
        List<TagFormat> fullScanPriority = FormatPriorityManager.getFullScanPriority();
        for (int i = 0; i < fullScanPriority.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, fullScanPriority.get(i));
        }

        System.out.println("\nComfort Scan Prioritäten für MP3:");
        List<TagFormat> mp3Priority = FormatPriorityManager.getComfortScanPriority("mp3");
        for (int i = 0; i < mp3Priority.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, mp3Priority.get(i));
        }

        System.out.println("\nUnterstützte Dateiendungen:");
        List<String> supportedExtensions = FormatPriorityManager.getSupportedExtensions();
        System.out.println("  " + String.join(", ", supportedExtensions));

        // Neue Priorität hinzufügen (Beispiel)
        System.out.println("\nBeispiel: Neue Priorität für .test Dateien hinzufügen");
        List<TagFormat> customPriority = Arrays.asList(
                TagFormat.ID3V2_4, TagFormat.VORBIS_COMMENT, TagFormat.MP4
        );
        FormatPriorityManager.addExtensionPriority("test", customPriority);

        System.out.println("Ist 'test' jetzt unterstützt? " +
                FormatPriorityManager.isExtensionSupported("test"));

        // Wieder entfernen
        FormatPriorityManager.removeExtensionPriority("test");
        System.out.println("Nach Entfernung - ist 'test' noch unterstützt? " +
                FormatPriorityManager.isExtensionSupported("test"));

        System.out.println();
    }

    /**
     * Erweiterte Anwendungsbeispiele
     */
    public static class AdvancedExamples {

        /**
         * Optimierte Verarbeitung für große Dateiensammlungen
         */
        public static void optimizedBatchProcessing(List<String> filePaths) throws IOException {
            System.out.println("=== OPTIMIERTE BATCH-VERARBEITUNG ===");

            MetadataManager manager = new MetadataManager();

            // Gruppiere Dateien nach Endung für optimale Verarbeitung
            Map<String, List<String>> filesByExtension = new java.util.HashMap<>();

            for (String filePath : filePaths) {
                String extension = getFileExtension(filePath);
                filesByExtension.computeIfAbsent(extension, k -> new java.util.ArrayList<>()).add(filePath);
            }

            // Verarbeite jede Gruppe mit optimierter Konfiguration
            for (Map.Entry<String, List<String>> entry : filesByExtension.entrySet()) {
                String extension = entry.getKey();
                List<String> files = entry.getValue();

                System.out.printf("Verarbeite %d %s-Dateien...%n", files.size(), extension.toUpperCase());

                // Verwende Comfort Scan für bekannte Endungen, Full Scan für unbekannte
                ScanConfiguration config = FormatPriorityManager.isExtensionSupported(extension) ?
                        ScanConfiguration.comfortScan() : ScanConfiguration.fullScan();

                Map<String, Integer> results = manager.readFromFiles(files, config);

                int totalMetadata = results.values().stream().mapToInt(Integer::intValue).sum();
                System.out.printf("  → %d Metadaten-Container geladen%n", totalMetadata);
            }

            System.out.printf("Gesamt: %d Metadaten-Container, %d Felder%n",
                    manager.getMetadataCount(), manager.getTotalFieldCount());
        }

        /**
         * Adaptive Scan-Strategie basierend auf Dateigröße
         */
        public static void adaptiveScanStrategy(String filePath) throws IOException {
            System.out.println("=== ADAPTIVE SCAN-STRATEGIE ===");

            java.io.File file = new java.io.File(filePath);
            long fileSize = file.length();

            ScanConfiguration config;

            if (fileSize < 1024 * 1024) { // < 1 MB
                // Kleine Dateien: Full Scan (wenig Overhead)
                config = ScanConfiguration.fullScan();
                System.out.println("Kleine Datei detected → Full Scan");

            } else if (fileSize < 100 * 1024 * 1024) { // < 100 MB
                // Mittlere Dateien: Comfort Scan
                config = ScanConfiguration.comfortScan();
                System.out.println("Mittlere Datei detected → Comfort Scan");

            } else {
                // Große Dateien: Nur moderne, häufige Formate
                config = ScanConfiguration.customScan(
                        TagFormat.ID3V2_4, TagFormat.ID3V2_3, TagFormat.VORBIS_COMMENT, TagFormat.MP4
                );
                System.out.println("Große Datei detected → Custom Scan (nur moderne Formate)");
            }

            List<TagInfo> tags = TagFormatDetector.detectTagFormats(filePath, config);
            System.out.printf("Ergebnis: %d Tag(s) in %.2f MB Datei%n",
                    tags.size(), fileSize / (1024.0 * 1024.0));
        }

        private static String getFileExtension(String filePath) {
            int dotIndex = filePath.lastIndexOf('.');
            return dotIndex == -1 ? "" : filePath.substring(dotIndex + 1).toLowerCase();
        }
    }
}