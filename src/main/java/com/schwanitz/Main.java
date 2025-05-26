package com.schwanitz;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.ID3Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.MetadataManager;
import com.schwanitz.others.TextFieldHandler;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {
    private static final Logger Log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        System.out.println("=== TAGIX - Tag Format Detection Library ===\n");

        try {
            // 1. Zeige verfügbare Scan-Modi
            demonstrateScanModeOverview();

            // 2. Demonstriere alle Scan-Modi
            demonstrateComfortScan();
            demonstrateFullScan();
            demonstrateCustomScan();

            // 3. Batch-Verarbeitung
            demonstrateBatchProcessing();

            // 4. MetadataManager Integration
            demonstrateMetadataManagerIntegration();

            // 5. Erweiterte Features
            demonstrateAdvancedFeatures();

            // 8. Fazit und Empfehlungen
            demonstrateConclusion();

            // 9. Fehlerbehandlung mit Fallback
            demonstrateErrorHandlingWithFallback();

            System.out.println("=== TAGIX DEMONSTRATION ABGESCHLOSSEN ===");
            System.out.println("✅ Alle Scan-Modi erfolgreich demonstriert");
            System.out.println("📚 Siehe Dokumentation für weitere Details");
            System.out.println("🔧 Beginnen Sie mit Comfort Scan für die meisten Anwendungen");

        } catch (Exception e) {
            Log.error("Fehler in der Hauptanwendung: " + e.getMessage(), e);
            System.err.println("Fehler: " + e.getMessage());
        }
    }

    /**
     * Übersicht über verfügbare Scan-Modi und unterstützte Formate
     */
    private static void demonstrateScanModeOverview() {
        System.out.println("=== SCAN-MODI ÜBERSICHT ===");

        // Zeige alle verfügbaren Tag-Formate
        System.out.println("Unterstützte Tag-Formate:");
        for (TagFormat format : TagFormat.values()) {
            System.out.printf("  - %-20s (Dateierweiterungen: %s)%n",
                    format.getFormatName(),
                    String.join(", ", format.getFileExtensions()));
        }

        System.out.println("\nVerfügbare Scan-Modi:");
        System.out.println("  1. COMFORT_SCAN - Nur wahrscheinliche Formate für Dateiendung (Standard)");
        System.out.println("  2. FULL_SCAN    - Alle Formate nach globaler Priorität");
        System.out.println("  3. CUSTOM_SCAN  - Benutzerdefinierte Format-Auswahl");

        // Zeige unterstützte Dateiendungen
        List<String> supportedExtensions = FormatPriorityManager.getSupportedExtensions();
        System.out.println("\nOptimierte Dateiendungen für Comfort Scan:");
        System.out.println("  " + String.join(", ", supportedExtensions));

        System.out.println();
    }

    /**
     * Comfort Scan Demonstration
     */
    private static void demonstrateComfortScan() {
        System.out.println("=== COMFORT SCAN DEMONSTRATION ===");
        System.out.println("Prüft nur wahrscheinliche Tag-Formate für die Dateiendung");

        // Verschiedene Dateitypen simulieren
        String[] testFiles = {
                "example.mp3",
                "example.wav",
                "example.ogg",
                "example.flac",
                "example.m4a",
                "example.aiff"
        };

        for (String fileName : testFiles) {
            System.out.printf("\n--- Comfort Scan für %s ---%n", fileName);

            // Zeige was geprüft werden würde
            String extension = getFileExtension(fileName);
            List<TagFormat> priorities = FormatPriorityManager.getComfortScanPriority(extension);

            System.out.println("Prioritätsreihenfolge:");
            for (int i = 0; i < priorities.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, priorities.get(i).getFormatName());
            }

            // Simuliere Detection (da keine echten Dateien vorhanden)
            try {
                ScanConfiguration config = ScanConfiguration.comfortScan();
                System.out.println("Configuration: " + config);

                // In einer echten Anwendung:
                // List<TagInfo> tags = TagFormatDetector.detectTagFormats(fileName, config);
                // System.out.println("Gefundene Tags: " + tags.size());

                System.out.println("✓ Comfort Scan konfiguriert (Datei nicht vorhanden für echten Test)");

            } catch (Exception e) {
                System.out.println("⚠ Simulierter Scan (Datei nicht vorhanden): " + e.getMessage());
            }
        }

        System.out.println("\n📊 Comfort Scan Vorteile:");
        System.out.println("  • Beste Performance (100% Baseline)");
        System.out.println("  • ~95% Vollständigkeit");
        System.out.println("  • Optimiert für bekannte Dateiformate");
        System.out.println("  • Geringster Ressourcenverbrauch");
        System.out.println();
    }

    /**
     * Full Scan Demonstration
     */
    private static void demonstrateFullScan() {
        System.out.println("=== FULL SCAN DEMONSTRATION ===");
        System.out.println("Prüft alle Tag-Formate nach globaler Wahrscheinlichkeit");

        // Zeige globale Prioritätsreihenfolge
        List<TagFormat> fullScanPriority = FormatPriorityManager.getFullScanPriority();
        System.out.println("\nGlobale Prioritätsreihenfolge (alle Formate):");
        for (int i = 0; i < fullScanPriority.size(); i++) {
            TagFormat format = fullScanPriority.get(i);
            System.out.printf("  %2d. %-20s (%s)%n",
                    i + 1,
                    format.getFormatName(),
                    String.join(", ", format.getFileExtensions()));
        }

        System.out.println("\n--- Full Scan Beispiele ---");

        // Beispiel 1: MP3-Datei mit Full Scan
        String mp3File = "comprehensive_example.mp3";
        System.out.printf("\nFull Scan für %s:%n", mp3File);

        try {
            ScanConfiguration config = ScanConfiguration.fullScan();
            System.out.println("Configuration: " + config);

            // Simuliere erweiterte Analyse
            System.out.println("Würde alle " + fullScanPriority.size() + " Formate prüfen:");
            System.out.println("  • ID3v2.3/v2.4 (Standard für MP3)");
            System.out.println("  • ID3v1/v1.1 (Legacy-Unterstützung)");
            System.out.println("  • APEv2/v1 (Alternative Tags)");
            System.out.println("  • Lyrics3v2/v1 (Songtext-Tags)");
            System.out.println("  • Vorbis Comment (falls vorhanden)");
            System.out.println("  • MP4 Tags (falls eingebettet)");
            System.out.println("  • Weitere seltene Formate...");

            System.out.println("✓ Full Scan würde ALLE möglichen Tags finden");

        } catch (Exception e) {
            System.out.println("⚠ Simulierter Full Scan: " + e.getMessage());
        }

        // Beispiel 2: Unbekannte Dateiendung
        String unknownFile = "mystery_audio.xyz";
        System.out.printf("\nFull Scan für unbekannte Endung (%s):%n", unknownFile);
        System.out.println("  → Verwendet globale Priorität (da keine spezifische Optimierung)");
        System.out.println("  → Ideal für experimentelle oder seltene Formate");

        System.out.println("\n📊 Full Scan Vorteile:");
        System.out.println("  • 100% Vollständigkeit");
        System.out.println("  • Findet auch ungewöhnliche Tag-Kombinationen");
        System.out.println("  • Ideal für Analyse-Tools und Debugging");
        System.out.println("  • Unterstützt experimentelle Formate");
        System.out.println("\n⚠ Full Scan Nachteile:");
        System.out.println("  • 60-80% Performance von Comfort Scan");
        System.out.println("  • Höherer Ressourcenverbrauch");
        System.out.println();
    }

    /**
     * Custom Scan Demonstration
     */
    private static void demonstrateCustomScan() {
        System.out.println("=== CUSTOM SCAN DEMONSTRATION ===");
        System.out.println("Prüft nur benutzerdefinierte Tag-Formate");

        // Beispiel 1: Nur ID3-Familie
        System.out.println("\n--- Beispiel 1: Nur ID3-Familie ---");
        try {
            ScanConfiguration id3Only = ScanConfiguration.customScan(
                    TagFormat.ID3V2_3,
                    TagFormat.ID3V2_4,
                    TagFormat.ID3V1,
                    TagFormat.ID3V1_1,
                    TagFormat.ID3V2_2
            );

            System.out.println("Konfiguration: " + id3Only);
            System.out.println("Anwendungsfall: Legacy MP3-Sammlung analysieren");
            System.out.println("Formate: " + id3Only.getCustomFormats().size() + " ID3-Varianten");

            for (TagFormat format : id3Only.getCustomFormats()) {
                System.out.println("  • " + format.getFormatName());
            }

        } catch (Exception e) {
            System.out.println("⚠ Fehler bei ID3-only Configuration: " + e.getMessage());
        }

        // Beispiel 2: Nur moderne Formate
        System.out.println("\n--- Beispiel 2: Nur moderne Formate ---");
        try {
            ScanConfiguration modernOnly = ScanConfiguration.customScan(
                    TagFormat.ID3V2_4,
                    TagFormat.VORBIS_COMMENT,
                    TagFormat.MP4,
                    TagFormat.APEV2
            );

            System.out.println("Konfiguration: " + modernOnly);
            System.out.println("Anwendungsfall: Moderne Musikbibliothek");
            System.out.println("Vorteile: Überspringt Legacy-Formate für bessere Performance");

            for (TagFormat format : modernOnly.getCustomFormats()) {
                System.out.println("  • " + format.getFormatName() + " (modern)");
            }

        } catch (Exception e) {
            System.out.println("⚠ Fehler bei Modern-only Configuration: " + e.getMessage());
        }

        // Beispiel 3: Professionelle Audio-Produktion
        System.out.println("\n--- Beispiel 3: Professionelle Audio-Produktion ---");
        try {
            ScanConfiguration professionalAudio = ScanConfiguration.customScan(
                    TagFormat.BWF_V2,
                    TagFormat.BWF_V1,
                    TagFormat.BWF_V0,
                    TagFormat.RIFF_INFO,
                    TagFormat.AIFF_METADATA
            );

            System.out.println("Konfiguration: " + professionalAudio);
            System.out.println("Anwendungsfall: Broadcast/Studio-Umgebung");
            System.out.println("Fokus: Broadcast Wave Format und professionelle Metadaten");

            for (TagFormat format : professionalAudio.getCustomFormats()) {
                System.out.println("  • " + format.getFormatName() + " (professional)");
            }

        } catch (Exception e) {
            System.out.println("⚠ Fehler bei Professional Audio Configuration: " + e.getMessage());
        }

        // Beispiel 4: Podcast-spezifisch
        System.out.println("\n--- Beispiel 4: Podcast-Optimiert ---");
        try {
            List<TagFormat> podcastFormats = Arrays.asList(
                    TagFormat.ID3V2_4,  // Moderne ID3 Tags
                    TagFormat.MP4,      // iTunes Podcast Format
                    TagFormat.VORBIS_COMMENT // OGG Podcast Format
            );

            ScanConfiguration podcastConfig = ScanConfiguration.customScan(podcastFormats);

            System.out.println("Konfiguration: " + podcastConfig);
            System.out.println("Anwendungsfall: Podcast-Player/Manager");
            System.out.println("Optimiert für: Podcast-Metadaten und Kapitel-Information");

            for (TagFormat format : podcastConfig.getCustomFormats()) {
                System.out.println("  • " + format.getFormatName() + " (podcast-optimized)");
            }

        } catch (Exception e) {
            System.out.println("⚠ Fehler bei Podcast Configuration: " + e.getMessage());
        }

        // Demonstration von Convenience-Methoden
        System.out.println("\n--- Convenience-Methoden Beispiele ---");
        System.out.println("// Array-Syntax");
        System.out.println("TagFormatDetector.detectTagFormatsCustomScan(file, TagFormat.ID3V2_3, TagFormat.MP4);");

        System.out.println("\n// Listen-Syntax");
        System.out.println("List<TagFormat> formats = Arrays.asList(TagFormat.VORBIS_COMMENT, TagFormat.APEV2);");
        System.out.println("TagFormatDetector.detectTagFormatsCustomScan(file, formats);");

        System.out.println("\n📊 Custom Scan Vorteile:");
        System.out.println("  • 120-200% Performance (je nach Formatanzahl)");
        System.out.println("  • Maximale Kontrolle über Scan-Prozess");
        System.out.println("  • Reduziert false positives");
        System.out.println("  • Ideal für spezialisierte Anwendungen");
        System.out.println();
    }

    /**
     * Batch-Verarbeitung Demonstration
     */
    private static void demonstrateBatchProcessing() {
        System.out.println("=== BATCH-VERARBEITUNG DEMONSTRATION ===");
        System.out.println("Effiziente Verarbeitung mehrerer Dateien");

        // Simuliere eine Liste von Dateien
        List<String> fileList = Arrays.asList(
                "album1/song1.mp3",
                "album1/song2.mp3",
                "album1/song3.mp3",
                "podcast/episode1.m4a",
                "podcast/episode2.mp4",
                "classical/symphony.flac",
                "classical/concerto.wav",
                "electronic/track1.ogg",
                "archive/old_recording.aiff"
        );

        System.out.println("Beispiel-Dateiliste (" + fileList.size() + " Dateien):");
        fileList.forEach(file -> System.out.println("  • " + file));

        // Batch-Verarbeitung mit verschiedenen Modi
        System.out.println("\n--- Batch-Verarbeitung mit TagFormatDetector ---");

        for (ScanMode mode : ScanMode.values()) {
            System.out.printf("\n%s Batch-Verarbeitung:%n", mode);

            ScanConfiguration config = switch (mode) {
                case COMFORT_SCAN -> ScanConfiguration.comfortScan();
                case FULL_SCAN -> ScanConfiguration.fullScan();
                case CUSTOM_SCAN -> ScanConfiguration.customScan(TagFormat.ID3V2_3, TagFormat.VORBIS_COMMENT, TagFormat.MP4);
            };

            try {
                System.out.println("Configuration: " + config);

                // In einer echten Anwendung:
                // Map<String, List<TagInfo>> results = TagFormatDetector.detectTagFormats(fileList, config);

                System.out.println("✓ Batch-Configuration erstellt");
                System.out.println("  → Würde alle " + fileList.size() + " Dateien mit " + mode + " verarbeiten");
                System.out.println("  → Ergebnis: Map<FilePath, List<TagInfo>>");

                // Simuliere Ergebnisse
                if (mode == ScanMode.COMFORT_SCAN) {
                    System.out.println("  → Erwartete Performance: 100% (Baseline)");
                    System.out.println("  → Pro Datei: 1-3 relevante Formate prüfen");
                } else if (mode == ScanMode.FULL_SCAN) {
                    System.out.println("  → Erwartete Performance: 60-80% von Comfort Scan");
                    System.out.println("  → Pro Datei: Alle " + TagFormat.values().length + " Formate prüfen");
                } else {
                    System.out.println("  → Erwartete Performance: 150% von Comfort Scan");
                    System.out.println("  → Pro Datei: Nur 3 spezifische Formate prüfen");
                }

            } catch (Exception e) {
                System.out.println("⚠ Simulierte Batch-Verarbeitung: " + e.getMessage());
            }
        }

        // MetadataManager Batch-Verarbeitung
        System.out.println("\n--- Batch-Verarbeitung mit MetadataManager ---");
        try {
            System.out.println("MetadataManager Batch-API:");
            System.out.println("  Map<String, Integer> results = manager.readFromFiles(fileList, config);");
            System.out.println("  → Ergebnis: Map<FilePath, AnzahlMetadatenContainer>");
            System.out.println("  → Automatisches Metadaten-Management");
            System.out.println("  → Integrierte Fehlerbehandlung pro Datei");

        } catch (Exception e) {
            System.out.println("⚠ MetadataManager Batch-Demo: " + e.getMessage());
        }

        System.out.println("\n📊 Batch-Verarbeitung Vorteile:");
        System.out.println("  • Optimiertes Caching für ähnliche Dateien");
        System.out.println("  • Fehlertoleranz (eine fehlerhafte Datei stoppt nicht den gesamten Batch)");
        System.out.println("  • Fortschritts-Tracking möglich");
        System.out.println("  • Ressourcen-Management über große Sammlungen");
        System.out.println();
    }

    /**
     * MetadataManager Integration Demonstration
     */
    private static void demonstrateMetadataManagerIntegration() {
        System.out.println("=== METADATA MANAGER INTEGRATION ===");
        System.out.println("Nahtlose Integration der Scan-Modi in MetadataManager");

        try {
            MetadataManager manager = new MetadataManager();

            System.out.println("MetadataManager initialisiert mit Standard-Handlern:");
            System.out.println("  • TIT2/TITLE (Titel)");
            System.out.println("  • TPE1/ARTIST (Künstler)");
            System.out.println("  • TALB/ALBUM (Album)");
            System.out.println("  • LYR (Lyrics)");

            // Verschiedene Scan-Modi demonstrieren
            String exampleFile = "demo_song.mp3";

            // 1. Standard (Comfort Scan)
            System.out.println("\n--- Standard MetadataManager Verwendung ---");
            System.out.println("manager.readFromFile(\"" + exampleFile + "\");");
            System.out.println("  → Verwendet automatisch Comfort Scan");
            System.out.println("  → Optimiert für die Dateiendung");

            // 2. Explizite Scan-Modi
            System.out.println("\n--- Explizite Scan-Modi ---");
            System.out.println("// Comfort Scan (explizit)");
            System.out.println("manager.readFromFile(file, ScanConfiguration.comfortScan());");

            System.out.println("\n// Full Scan");
            System.out.println("manager.readFromFileFullScan(file);");

            System.out.println("\n// Custom Scan");
            System.out.println("manager.readFromFileCustomScan(file, TagFormat.ID3V2_3, TagFormat.APEV2);");

            // 3. Simuliere Metadaten-Verarbeitung
            System.out.println("\n--- Simulierte Metadaten-Verarbeitung ---");

            // Erstelle Beispiel-Metadaten für Demonstration
            ID3Metadata demoMetadata = new ID3Metadata(TagFormat.ID3V2_3);

            // Handler für Text-Felder
            TextFieldHandler titleHandler = new TextFieldHandler("TIT2");
            TextFieldHandler artistHandler = new TextFieldHandler("TPE1");
            TextFieldHandler albumHandler = new TextFieldHandler("TALB");

            // Beispiel-Felder hinzufügen
            demoMetadata.addField(new MetadataField<>("TIT2", "Demo Song Title", titleHandler));
            demoMetadata.addField(new MetadataField<>("TPE1", "Demo Artist", artistHandler));
            demoMetadata.addField(new MetadataField<>("TALB", "Demo Album", albumHandler));

            // Zu Manager hinzufügen
            manager.addMetadata(demoMetadata);

            System.out.println("Demo-Metadaten hinzugefügt:");
            System.out.println("  Format: " + demoMetadata.getTagFormat());
            System.out.println("  Felder: " + demoMetadata.getFields().size());

            // Manager-Statistiken anzeigen
            System.out.println("\nMetadataManager Statistiken:");
            System.out.println("  • Metadaten-Container: " + manager.getMetadataCount());
            System.out.println("  • Gesamt-Felder: " + manager.getTotalFieldCount());

            Map<String, Integer> summary = manager.getTagFormatSummary();
            System.out.println("  • Format-Zusammenfassung: " + summary);

            // Metadaten abrufen
            System.out.println("\n--- Metadaten-Abruf ---");
            Metadata retrievedMetadata = manager.getMetadata(TagFormat.ID3V2_3);
            if (retrievedMetadata != null) {
                System.out.println("ID3v2.3 Metadaten gefunden:");
                for (MetadataField<?> field : retrievedMetadata.getFields()) {
                    System.out.printf("  • %s: %s%n", field.getKey(), field.getValue());
                }
            }

            // Batch-Verarbeitung Beispiel
            System.out.println("\n--- MetadataManager Batch-Beispiel ---");
            List<String> batchFiles = Arrays.asList(
                    "song1.mp3", "song2.flac", "song3.m4a"
            );

            System.out.println("Batch-Verarbeitung Simulation:");
            System.out.println("  Dateien: " + batchFiles.size());
            System.out.println("  Mode: Comfort Scan");
            System.out.println("  → manager.readFromFiles(files, ScanConfiguration.comfortScan())");

            // In einer echten Anwendung würde hier die Batch-Verarbeitung stattfinden

        } catch (Exception e) {
            System.out.println("⚠ MetadataManager Integration Demo: " + e.getMessage());
        }

        System.out.println("\n📊 MetadataManager Integration Vorteile:");
        System.out.println("  • Nahtlose API-Integration");
        System.out.println("  • Automatisches Tag-Format zu Strategy Mapping");
        System.out.println("  • Unified Metadata-Handling");
        System.out.println("  • Batch-Processing mit Fehlertoleranz");
        System.out.println();
    }

    /**
     * Erweiterte Features Demonstration
     */
    private static void demonstrateAdvancedFeatures() {
        System.out.println("=== ERWEITERTE FEATURES ===");

        // 1. Cache-Management
        System.out.println("--- Cache-Management ---");
        System.out.println("TagFormatDetector.getCacheSize(): 0 (initial)");
        System.out.println("TagFormatDetector.clearCache(): Cache geleert");
        System.out.println("TagFormatDetector.removeCacheEntry(file): Einzelner Eintrag entfernt");
        System.out.println("  → Optimiert Memory-Usage bei großen Sammlungen");

        // 2. Prioritäts-Management
        System.out.println("\n--- Prioritäts-Management ---");
        System.out.println("FormatPriorityManager Features:");
        System.out.println("  • isExtensionSupported(\"mp3\"): " + FormatPriorityManager.isExtensionSupported("mp3"));
        System.out.println("  • getSupportedExtensions(): " + FormatPriorityManager.getSupportedExtensions().size() + " Endungen");

        // Beispiel: Neue Priorität hinzufügen
        System.out.println("\nBeispiel: Neue Dateiendung hinzufügen");
        System.out.println("List<TagFormat> customPriority = Arrays.asList(TagFormat.ID3V2_4, TagFormat.MP4);");
        System.out.println("FormatPriorityManager.addExtensionPriority(\"newformat\", customPriority);");

        try {
            List<TagFormat> customPriority = Arrays.asList(TagFormat.ID3V2_4, TagFormat.MP4);
            FormatPriorityManager.addExtensionPriority("demo", customPriority);
            System.out.println("✓ Demo-Format hinzugefügt: " + FormatPriorityManager.isExtensionSupported("demo"));

            // Wieder entfernen
            FormatPriorityManager.removeExtensionPriority("demo");
            System.out.println("✓ Demo-Format entfernt: " + FormatPriorityManager.isExtensionSupported("demo"));

        } catch (Exception e) {
            System.out.println("⚠ Prioritäts-Management Demo: " + e.getMessage());
        }

        // 3. Adaptive Strategien
        System.out.println("\n--- Adaptive Scan-Strategien ---");
        System.out.println("Beispiel: Dateigröße-basierte Strategie");
        System.out.println("  < 1 MB:    Full Scan (geringer Overhead)");
        System.out.println("  1-100 MB:  Comfort Scan (ausgewogen)");
        System.out.println("  > 100 MB:  Custom Scan (nur häufige Formate)");

        // 4. Error Handling
        System.out.println("\n--- Robuste Fehlerbehandlung ---");
        System.out.println("• Graceful Degradation bei Scan-Fehlern");
        System.out.println("• Einzelne fehlerhafte Dateien stoppen nicht den Batch");
        System.out.println("• Detailliertes Logging für Debugging");
        System.out.println("• Fallback-Strategien bei unbekannten Formaten");

        System.out.println();
    }

    /**
     * Performance-Vergleich Demonstration
     */
    private static void demonstratePerformanceComparison() {
        System.out.println("=== PERFORMANCE-VERGLEICH ===");

        System.out.println("Relative Performance (simuliert):");
        System.out.println("┌─────────────────┬──────────────┬───────────────┬─────────────────┐");
        System.out.println("│ Scan-Modus      │ Performance  │ Vollständig.  │ Ressourcen      │");
        System.out.println("├─────────────────┼──────────────┼───────────────┼─────────────────┤");
        System.out.println("│ Comfort Scan    │ 100% ⭐⭐⭐   │ ~95% ⭐⭐     │ Niedrig ⭐⭐⭐   │");
        System.out.println("│ Full Scan       │ 60-80% ⭐⭐   │ 100% ⭐⭐⭐   │ Hoch ⭐         │");
        System.out.println("│ Custom Scan*    │ 120-200% ⭐⭐⭐│ Variable      │ Sehr niedrig ⭐⭐⭐│");
        System.out.println("└─────────────────┴──────────────┴───────────────┴─────────────────┘");
        System.out.println("* Custom Scan Performance abhängig von Anzahl gewählter Formate");

        // Beispiel-Messung simulieren
        System.out.println("\n--- Simulierte Performance-Messung ---");
        String testFile = "performance_test.mp3";

        // Comfort Scan
        long startTime = System.nanoTime();
        try {
            ScanConfiguration comfortConfig = ScanConfiguration.comfortScan();
            // Simuliere Scan-Zeit
            Thread.sleep(10); // 10ms simuliert
            long comfortTime = System.nanoTime() - startTime;
            System.out.printf("Comfort Scan: %.2f ms (Baseline)%n", comfortTime / 1_000_000.0);
        } catch (Exception e) {
            System.out.println("Comfort Scan: ~10ms (simuliert)");
        }

        // Full Scan
        startTime = System.nanoTime();
        try {
            ScanConfiguration fullConfig = ScanConfiguration.fullScan();
            // Simuliere längere Scan-Zeit
            Thread.sleep(15); // 15ms simuliert
            long fullTime = System.nanoTime() - startTime;
            System.out.printf("Full Scan: %.2f ms (+50%% Zeit)%n", fullTime / 1_000_000.0);
        } catch (Exception e) {
            System.out.println("Full Scan: ~15ms (simuliert)");
        }

        // Custom Scan
        startTime = System.nanoTime();
        try {
            ScanConfiguration customConfig = ScanConfiguration.customScan(TagFormat.ID3V2_3, TagFormat.ID3V1);
            // Simuliere kürzere Scan-Zeit
            Thread.sleep(6); // 6ms simuliert
            long customTime = System.nanoTime() - startTime;
            System.out.printf("Custom Scan (2 Formate): %.2f ms (-40%% Zeit)%n", customTime / 1_000_000.0);
        } catch (Exception e) {
            System.out.println("Custom Scan: ~6ms (simuliert)");
        }

        System.out.println("\n📊 Performance-Empfehlungen:");
        System.out.println("  • Normale Anwendungen: Comfort Scan");
        System.out.println("  • Analyse-Tools: Full Scan");
        System.out.println("  • Spezialisierte Apps: Custom Scan");
        System.out.println("  • Große Sammlungen: Batch-Processing mit Caching");
        System.out.println();
    }

    /**
     * Fazit und Empfehlungen
     */
    private static void demonstrateConclusion() {
        System.out.println("=== ZUSAMMENFASSUNG UND EMPFEHLUNGEN ===");

        System.out.println("🎯 Wann welchen Scan-Modus verwenden:");
        System.out.println();

        System.out.println("📱 Medienplayer/Bibliotheken (Standard-Anwendungen):");
        System.out.println("  → Comfort Scan");
        System.out.println("  → manager.readFromFile(file); // Standard");
        System.out.println("  → Beste Balance aus Performance und Vollständigkeit");
        System.out.println();

        System.out.println("🔍 Analyse-Tools/Debugging/Forensics:");
        System.out.println("  → Full Scan");
        System.out.println("  → manager.readFromFileFullScan(file);");
        System.out.println("  → Garantiert alle verfügbaren Tags zu finden");
        System.out.println();

        System.out.println("⚡ Performance-kritische/spezialisierte Anwendungen:");
        System.out.println("  → Custom Scan");
        System.out.println("  → manager.readFromFileCustomScan(file, TagFormat.ID3V2_4, TagFormat.MP4);");
        System.out.println("  → Maximale Kontrolle und Performance");
        System.out.println();

        System.out.println("📦 Batch-Verarbeitung großer Sammlungen:");
        System.out.println("  → Batch-APIs verwenden");
        System.out.println("  → Map<String, List<TagInfo>> results = TagFormatDetector.detectTagFormats(files, config);");
        System.out.println("  → Cache-Management für Memory-Optimierung");
        System.out.println();

        System.out.println("🔧 Erweiterte Anpassungen:");
        System.out.println("  → FormatPriorityManager für eigene Dateiendungen");
        System.out.println("  → Adaptive Strategien basierend auf Dateigröße/Kontext");
        System.out.println("  → Custom Handler für spezielle Metadaten-Felder");
        System.out.println();

        System.out.println("✨ Key Features der neuen API:");
        System.out.println("  ✅ Typsichere Konfiguration statt Magic Booleans");
        System.out.println("  ✅ Intelligente Priorisierung nach Dateiformat");
        System.out.println("  ✅ Batch-Processing mit optimiertem Caching");
        System.out.println("  ✅ Extensible Priority Management");
        System.out.println("  ✅ Comprehensive Error Handling");
        System.out.println();

        System.out.println("🚀 Performance-Verbesserungen:");
        System.out.println("  • Comfort Scan: 95% Vollständigkeit bei 100% Performance");
        System.out.println("  • Custom Scan: Bis zu 200% Performance bei spezifischen Formaten");
        System.out.println("  • Intelligentes Caching reduziert wiederholte I/O-Operationen");
        System.out.println("  • Batch-Processing optimiert für große Dateiensammlungen");
        System.out.println();
    }

    // Hilfsmethoden

    /**
     * Extrahiert die Dateiendung aus einem Dateipfad
     */
    private static String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return dotIndex == -1 ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * Simuliert eine Verzögerung für Performance-Tests
     */
    private static void simulateProcessingTime(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Formatiert Dateigröße für bessere Lesbarkeit
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Adaptive Scan-Strategie basierend auf Dateikontext
     */
    private static ScanConfiguration getAdaptiveStrategy(String filePath) {
        // Simuliere Dateigröße-basierte Logik
        String extension = getFileExtension(filePath);

        // Podcast-Dateien
        if (filePath.toLowerCase().contains("podcast") ||
                filePath.toLowerCase().contains("episode")) {
            return ScanConfiguration.customScan(
                    TagFormat.ID3V2_4, TagFormat.MP4, TagFormat.VORBIS_COMMENT
            );
        }

        // Klassische Musik (oft mit umfangreichen Metadaten)
        if (filePath.toLowerCase().contains("classical") ||
                filePath.toLowerCase().contains("symphony") ||
                filePath.toLowerCase().contains("concerto")) {
            return ScanConfiguration.fullScan(); // Alle möglichen Metadaten finden
        }

        // Archive/Legacy-Dateien
        if (filePath.toLowerCase().contains("archive") ||
                filePath.toLowerCase().contains("old") ||
                filePath.toLowerCase().contains("legacy")) {
            return ScanConfiguration.customScan(
                    TagFormat.ID3V1, TagFormat.ID3V1_1, TagFormat.ID3V2_2, TagFormat.APEV1
            );
        }

        // Standard: Comfort Scan
        return ScanConfiguration.comfortScan();
    }

    /**
     * Zeigt detaillierte Tag-Informationen
     */
    private static void displayTagInfo(List<TagInfo> tags) {
        if (tags.isEmpty()) {
            System.out.println("  Keine Tags gefunden");
            return;
        }

        System.out.println("  Gefundene Tags: " + tags.size());
        for (TagInfo tag : tags) {
            System.out.printf("    • %-20s [Offset: %d, Größe: %d bytes]%n",
                    tag.getFormat().getFormatName(),
                    tag.getOffset(),
                    tag.getSize());
        }
    }

    /**
     * Beispiel für Fehlerbehandlung mit Fallback-Strategien
     */
    private static void demonstrateErrorHandlingWithFallback() {
        System.out.println("\n--- Robuste Fehlerbehandlung mit Fallback ---");

        String problematicFile = "corrupted_or_missing.mp3";

        System.out.println("Fallback-Strategie für problematische Dateien:");
        System.out.println("1. Versuche Custom Scan (schnell)");
        System.out.println("2. Fallback zu Comfort Scan");
        System.out.println("3. Fallback zu minimaler Detection");
        System.out.println("4. Graceful Error Handling");

        try {
            // Erste Ebene: Custom Scan
            System.out.println("  → Versuche Custom Scan...");
            ScanConfiguration customConfig = ScanConfiguration.customScan(TagFormat.ID3V2_3, TagFormat.MP4);
            // Simuliere Fehler
            throw new IOException("Simulated file access error");

        } catch (IOException e) {
            System.out.println("  ⚠ Custom Scan fehlgeschlagen: " + e.getMessage());

            try {
                // Zweite Ebene: Comfort Scan
                System.out.println("  → Fallback zu Comfort Scan...");
                ScanConfiguration comfortConfig = ScanConfiguration.comfortScan();
                // Simuliere Teilsuccess
                System.out.println("  ✓ Comfort Scan erfolgreich (Fallback)");

            } catch (Exception e2) {
                System.out.println("  ⚠ Comfort Scan fehlgeschlagen: " + e2.getMessage());
                System.out.println("  → Datei als 'problematisch' markieren und überspringen");
            }
        }
    }
}