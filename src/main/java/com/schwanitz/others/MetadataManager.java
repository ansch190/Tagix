package com.schwanitz.others;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.context.TagParsingContext;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormatDetector;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetadataManager {
    private static final Logger Log = LoggerFactory.getLogger(MetadataManager.class);

    private final List<Metadata> metadataList = new ArrayList<>();
    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();
    private final TagParsingContext parsingContext = new TagParsingContext();

    public MetadataManager() {
        // Standard-Handler registrieren
        registerHandler(new TextFieldHandler("TIT2")); // Titel (ID3)
        registerHandler(new TextFieldHandler("TPE1")); // Künstler (ID3)
        registerHandler(new TextFieldHandler("TALB")); // Album (ID3)
        registerHandler(new TextFieldHandler("TITLE")); // Titel (Vorbis)
        registerHandler(new TextFieldHandler("ARTIST")); // Künstler (Vorbis)
        registerHandler(new TextFieldHandler("ALBUM")); // Album (Vorbis)
        registerHandler(new TextFieldHandler("LYR")); // Songtext (Lyrics3)
    }

    public void registerHandler(FieldHandler<?> handler) {
        handlers.put(handler.getKey(), handler);
    }

    /**
     * Neue Hauptmethode für konfigurierbare Metadaten-Erkennung
     *
     * @param filePath Pfad zur Datei
     * @param config Scan-Konfiguration
     * @throws IOException bei Dateizugriffsfehlern
     */
    public void readFromFile(String filePath, ScanConfiguration config) throws IOException {
        Log.info("Reading metadata from {} with scan mode: {}", filePath, config.getMode());

        // Tag-Formate mit konfigurierter Strategie erkennen
        List<TagInfo> detectedTags = TagFormatDetector.detectTagFormats(filePath, config);

        Log.debug("Found {} tag(s) in file", detectedTags.size());

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            for (TagInfo tagInfo : detectedTags) {
                try {
                    // Strategy Pattern für Parsing verwenden
                    Metadata metadata = parsingContext.parseTag(
                            tagInfo.getFormat(),
                            file,
                            tagInfo.getOffset(),
                            tagInfo.getSize()
                    );
                    addMetadata(metadata);

                    Log.debug("Successfully parsed {} tag with {} fields",
                            tagInfo.getFormat(), metadata.getFields().size());

                } catch (UnsupportedOperationException e) {
                    Log.warn("Nicht unterstütztes Tag-Format: {}", tagInfo.getFormat());
                } catch (Exception e) {
                    Log.error("Error parsing {} tag: {}", tagInfo.getFormat(), e.getMessage());
                }
            }
        }

        Log.info("Successfully read {} metadata containers from {}", metadataList.size(), filePath);
    }

    /**
     * Convenience-Methode für Comfort Scan (Standard)
     */
    public void readFromFile(String filePath) throws IOException {
        readFromFile(filePath, ScanConfiguration.comfortScan());
    }

    /**
     * Convenience-Methode für Full Scan
     */
    public void readFromFileFullScan(String filePath) throws IOException {
        readFromFile(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Convenience-Methode für Custom Scan
     */
    public void readFromFileCustomScan(String filePath, TagFormat... formats) throws IOException {
        readFromFile(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Convenience-Methode für Custom Scan mit Liste
     */
    public void readFromFileCustomScan(String filePath, List<TagFormat> formats) throws IOException {
        readFromFile(filePath, ScanConfiguration.customScan(formats));
    }

    /**
     * Batch-Verarbeitung: Mehrere Dateien mit derselben Konfiguration
     */
    public Map<String, Integer> readFromFiles(List<String> filePaths, ScanConfiguration config) {
        Map<String, Integer> results = new HashMap<>();

        for (String filePath : filePaths) {
            int metadataCountBefore = metadataList.size();

            try {
                readFromFile(filePath, config);
                int metadataCountAfter = metadataList.size();
                int addedMetadata = metadataCountAfter - metadataCountBefore;
                results.put(filePath, addedMetadata);

            } catch (IOException e) {
                Log.error("Error processing file {}: {}", filePath, e.getMessage());
                results.put(filePath, 0); // 0 Metadaten bei Fehler
            }
        }

        return results;
    }

    /**
     * Backward Compatibility: Alte Methode beibehalten
     * @deprecated Verwende stattdessen readFromFile(String, ScanConfiguration)
     */
    @Deprecated
    public void readFromFile(String filePath, boolean fullSearch) throws IOException {
        if (fullSearch) {
            readFromFileFullScan(filePath);
        } else {
            readFromFile(filePath);
        }
    }

    public void writeToFile(String filePath) throws IOException {
        // Platzhalter: Logik zum Schreiben der Metadaten im richtigen Tag-Format
        Log.info("Writing metadata to {} (not yet implemented)", filePath);
    }

    /**
     * Metadaten nach Tag-Format abrufen
     */
    public Metadata getMetadata(String tagFormat) {
        for (Metadata metadata : metadataList) {
            if (metadata.getTagFormat().equals(tagFormat)) {
                return metadata;
            }
        }
        return null;
    }

    /**
     * Alle Metadaten eines bestimmten Tag-Formats abrufen
     */
    public List<Metadata> getAllMetadata(String tagFormat) {
        List<Metadata> result = new ArrayList<>();
        for (Metadata metadata : metadataList) {
            if (metadata.getTagFormat().equals(tagFormat)) {
                result.add(metadata);
            }
        }
        return result;
    }

    /**
     * Metadaten nach Tag-Format-Enum abrufen
     */
    public Metadata getMetadata(TagFormat tagFormat) {
        return getMetadata(tagFormat.getFormatName());
    }

    /**
     * Alle Metadaten eines bestimmten Tag-Format-Enums abrufen
     */
    public List<Metadata> getAllMetadata(TagFormat tagFormat) {
        return getAllMetadata(tagFormat.getFormatName());
    }

    /**
     * Alle geladenen Metadaten abrufen
     */
    public List<Metadata> getAllMetadata() {
        return new ArrayList<>(metadataList);
    }

    /**
     * Anzahl der geladenen Metadaten-Container
     */
    public int getMetadataCount() {
        return metadataList.size();
    }

    /**
     * Gesamtanzahl aller Felder in allen Metadaten
     */
    public int getTotalFieldCount() {
        return metadataList.stream()
                .mapToInt(metadata -> metadata.getFields().size())
                .sum();
    }

    /**
     * Zusammenfassung der geladenen Tag-Formate
     */
    public Map<String, Integer> getTagFormatSummary() {
        Map<String, Integer> summary = new HashMap<>();

        for (Metadata metadata : metadataList) {
            String format = metadata.getTagFormat();
            summary.put(format, summary.getOrDefault(format, 0) + 1);
        }

        return summary;
    }

    /**
     * Metadaten-Container hinzufügen
     */
    public void addMetadata(Metadata metadata) {
        metadataList.add(metadata);
    }

    /**
     * Alle Metadaten löschen
     */
    public void clearMetadata() {
        metadataList.clear();
        Log.debug("All metadata cleared");
    }

    /**
     * Metadaten eines bestimmten Formats entfernen
     */
    public boolean removeMetadata(String tagFormat) {
        boolean removed = metadataList.removeIf(metadata ->
                metadata.getTagFormat().equals(tagFormat));

        if (removed) {
            Log.debug("Removed metadata for format: {}", tagFormat);
        }

        return removed;
    }

    /**
     * Metadaten eines bestimmten Format-Enums entfernen
     */
    public boolean removeMetadata(TagFormat tagFormat) {
        return removeMetadata(tagFormat.getFormatName());
    }

    /**
     * Debug-Ausgabe aller Metadaten
     */
    public void printMetadataSummary() {
        Log.info("=== Metadata Summary ===");
        Log.info("Total containers: {}", metadataList.size());
        Log.info("Total fields: {}", getTotalFieldCount());

        Map<String, Integer> summary = getTagFormatSummary();
        for (Map.Entry<String, Integer> entry : summary.entrySet()) {
            Log.info("Format '{}': {} container(s)", entry.getKey(), entry.getValue());
        }

        // Detaillierte Feldauflistung
        for (Metadata metadata : metadataList) {
            Log.info("--- {} ({} fields) ---",
                    metadata.getTagFormat(), metadata.getFields().size());

            for (MetadataField<?> field : metadata.getFields()) {
                Object value = field.getValue();
                String valueStr = value != null ? value.toString() : "null";

                // Lange Werte kürzen
                if (valueStr.length() > 100) {
                    valueStr = valueStr.substring(0, 97) + "...";
                }

                Log.info("  {}: {}", field.getKey(), valueStr);
            }
        }
        Log.info("=== End Summary ===");
    }
}