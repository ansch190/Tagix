package com.schwanitz.api;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.strategies.writing.factory.TagWritingStrategyFactory;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;

/**
 * Zentrale API zum Schreiben und Aktualisieren von Metadaten in Audiodateien.
 * <p>
 * Bietet verschiedene Methoden zum Schreiben von Tags:
 * <ul>
 *   <li>Volle Metadata-Objekte schreiben</li>
 *   <li>Einzelne Felder aktualisieren (Convenience)</li>
 *   <li>Tags entfernen</li>
 *   <li>Batch-Verarbeitung mehrerer Dateien</li>
 * </ul>
 * <p>
 * Unterstützt verschiedene Schreibmodi über {@link WriteConfiguration}:
 * <ul>
 *   <li>{@code CREATE_NEW} – Nur neue Tags schreiben</li>
 *   <li>{@code UPDATE_EXISTING} – Bestehende Tags aktualisieren</li>
 *   <li>{@code REPLACE_ALL} – Tags vollständig ersetzen</li>
 *   <li>{@code REMOVE} – Tags entfernen</li>
 * </ul>
 * </p>
 *
 * @see MetadataManager
 * @see WriteConfiguration
 * @see WriteResult
 */
public class TagWriter {

    private static final Logger LOG = LoggerFactory.getLogger(TagWriter.class);

    private final TagFormatDetector detector;
    private final TagParsingStrategyFactory parsingFactory;
    private final TagWritingStrategyFactory writingFactory;

    /**
     * Erstellt einen neuen {@code TagWriter} mit Standard-Factories.
     */
    public TagWriter() {
        this(new TagFormatDetector(), new TagParsingStrategyFactory(), new TagWritingStrategyFactory());
    }

    /**
     * Erstellt einen neuen {@code TagWriter} mit voller Dependency Injection.
     *
     * @param detector       der zu verwendende TagFormatDetector
     * @param parsingFactory die Factory zum Lesen bestehender Tags
     * @param writingFactory die Factory zum Schreiben von Tags
     */
    public TagWriter(TagFormatDetector detector,
                     TagParsingStrategyFactory parsingFactory,
                     TagWritingStrategyFactory writingFactory) {
        this.detector = Objects.requireNonNull(detector, "detector must not be null");
        this.parsingFactory = Objects.requireNonNull(parsingFactory, "parsingFactory must not be null");
        this.writingFactory = Objects.requireNonNull(writingFactory, "writingFactory must not be null");
    }

    // ================================
    // EINZELDATEI: VOLLE METADATA
    // ================================

    /**
     * Schreibt Metadaten in eine Datei mit Standard-Konfiguration.
     *
     * @param filePath der Dateipfad
     * @param metadata die zu schreibenden Metadaten
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult writeTags(String filePath, Metadata metadata) throws IOException {
        return writeTags(filePath, metadata, WriteConfiguration.defaults());
    }

    /**
     * Schreibt Metadaten in eine Datei mit der angegebenen Konfiguration.
     *
     * @param filePath der Dateipfad
     * @param metadata die zu schreibenden Metadaten
     * @param config   die Schreib-Konfiguration
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult writeTags(String filePath, Metadata metadata, WriteConfiguration config) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Path path = Path.of(filePath);
        return writeTags(path, metadata, config);
    }

    /**
     * Schreibt Metadaten in eine Datei anhand eines NIO-Pfads.
     *
     * @param path     der NIO-Pfad
     * @param metadata die zu schreibenden Metadaten
     * @param config   die Schreib-Konfiguration
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult writeTags(Path path, Metadata metadata, WriteConfiguration config) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(config, "config must not be null");

        try (SeekableDataSource source = SeekableDataSources.forPath(path)) {
            return writeTags(source, metadata, config);
        }
    }

    /**
     * Schreibt Metadaten in eine {@link SeekableDataSource}.
     * <p>
     * Dies ist die zentrale Methode, die alle anderen delegiert.
     * </p>
     *
     * @param source   die Datenquelle
     * @param metadata die zu schreibenden Metadaten
     * @param config   die Schreib-Konfiguration
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult writeTags(SeekableDataSource source, Metadata metadata,
                                  WriteConfiguration config) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(config, "config must not be null");

        // 1. Bestehende Tags erkennen
        List<TagInfo> detectedTags = detector.fullScan(source);

        // 2. Ziel-Format bestimmen
        TagFormat targetFormat = determineTargetFormat(metadata, detectedTags, config);
        if (targetFormat == null) {
            return WriteResult.failure(null, "Kein Ziel-Format bestimmt");
        }

        // 3. Bestehendes Tag finden
        TagInfo existingTag = findExistingTag(detectedTags, targetFormat);

        // 4. Schreib-Strategie auswählen
        TagWritingStrategy strategy = writingFactory.getStrategyForFormat(targetFormat);
        if (strategy == null) {
            return WriteResult.failure(targetFormat, "Keine Schreib-Strategie für " + targetFormat);
        }

        // 5. Metadaten vorbereiten (Merge bei UPDATE)
        Metadata writeMetadata = prepareMetadata(metadata, source, existingTag, config);

        // 6. Schreiben
        LOG.debug("Schreibe {} Tags in {} (Mode: {})", targetFormat.getFormatName(),
                source.name(), config.mode());

        return strategy.writeTag(targetFormat, writeMetadata, source, existingTag, config);
    }

    // ================================
    // CONVENIENCE: EINZELNES FELD
    // ================================

    /**
     * Aktualisiert ein einzelnes Metadatenfeld in einer Datei.
     *
     * @param filePath der Dateipfad
     * @param key      der Feld-Schlüssel (z.B. "TIT2", "ARTIST")
     * @param value    der neue Wert
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult updateField(String filePath, String key, String value) throws IOException {
        return updateField(filePath, key, value, WriteConfiguration.defaults());
    }

    /**
     * Aktualisiert ein einzelnes Metadatenfeld mit der angegebenen Konfiguration.
     *
     * @param filePath der Dateipfad
     * @param key      der Feld-Schlüssel
     * @param value    der neue Wert
     * @param config   die Schreib-Konfiguration
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult updateField(String filePath, String key, String value,
                                    WriteConfiguration config) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(key, "key must not be null");

        try (SeekableDataSource source = SeekableDataSources.forPath(Path.of(filePath))) {
            // Bestehende Tags lesen
            List<TagInfo> detectedTags = detector.fullScan(source);
            TagFormat targetFormat = determineUpdateFormat(detectedTags, key);

            if (targetFormat == null) {
                // Kein bestehendes Tag: Neues mit Standard-Format erstellen
                targetFormat = config.id3Version() == 3 ? TagFormat.ID3V2_3 : TagFormat.ID3V2_4;
            }

            // Bestehendes Tag lesen
            TagInfo existingTag = findExistingTag(detectedTags, targetFormat);
            Metadata existing = readExistingMetadata(source, existingTag, targetFormat);

            // Feld aktualisieren/hinzufügen
            if (existing == null) {
                existing = new GenericMetadata(targetFormat);
            }

            // Altes Feld entfernen
            if (existing instanceof GenericMetadata gm) {
                gm.removeField(key);
            }

            // Neues Feld hinzufügen
            existing.addField(new MetadataField<>(key, value, new TextFieldHandler(key)));

            // Schreiben
            return writeTags(source, existing, config);
        }
    }

    // ================================
    // TAGS ENTFERNEN
    // ================================

    /**
     * Entfernt alle Tags eines bestimmten Formats aus einer Datei.
     *
     * @param filePath der Dateipfad
     * @param format   das zu entfernende Format
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult removeTags(String filePath, TagFormat format) throws IOException {
        return removeTags(filePath, format, WriteConfiguration.remove());
    }

    /**
     * Entfernt Tags mit der angegebenen Konfiguration.
     *
     * @param filePath der Dateipfad
     * @param format   das zu entfernende Format
     * @param config   die Schreib-Konfiguration
     * @return das Ergebnis des Schreibvorgangs
     * @throws IOException bei Ein-/Ausgabefehlern
     */
    public WriteResult removeTags(String filePath, TagFormat format,
                                   WriteConfiguration config) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(format, "format must not be null");

        WriteConfiguration removeConfig = new WriteConfiguration(
                WriteMode.REMOVE, config.inPlace(), config.id3Version(),
                config.encoding(), config.preserveExistingTags());

        try (SeekableDataSource source = SeekableDataSources.forPath(Path.of(filePath))) {
            List<TagInfo> detectedTags = detector.fullScan(source);
            TagInfo existingTag = findExistingTag(detectedTags, format);

            if (existingTag == null) {
                return WriteResult.success(format, 0, 0, "Kein Tag zum Entfernen gefunden");
            }

            TagWritingStrategy strategy = writingFactory.getStrategyForFormat(format);
            if (strategy == null) {
                return WriteResult.failure(format, "Keine Schreib-Strategie für " + format);
            }

            GenericMetadata emptyMetadata = new GenericMetadata(format);
            return strategy.writeTag(format, emptyMetadata, source, existingTag, removeConfig);
        }
    }

    // ================================
    // BATCH
    // ================================

    /**
     * Schreibt Metadaten in mehrere Dateien parallel.
     *
     * @param filePaths die Liste der Dateipfade
     * @param metadata  die zu schreibenden Metadaten
     * @return eine Zuordnung von Dateipfaden zu Schreib-Ergebnissen
     */
    public Map<String, WriteResult> writeTags(List<String> filePaths, Metadata metadata) {
        return writeTags(filePaths, metadata, WriteConfiguration.defaults());
    }

    /**
     * Schreibt Metadaten in mehrere Dateien parallel mit der angegebenen Konfiguration.
     *
     * @param filePaths die Liste der Dateipfade
     * @param metadata  die zu schreibenden Metadaten
     * @param config    die Schreib-Konfiguration
     * @return eine Zuordnung von Dateipfaden zu Schreib-Ergebnissen
     */
    public Map<String, WriteResult> writeTags(List<String> filePaths, Metadata metadata,
                                               WriteConfiguration config) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(config, "config must not be null");

        List<String> validPaths = filePaths.stream().filter(Objects::nonNull).toList();
        if (validPaths.isEmpty()) {
            return Map.of();
        }

        if (validPaths.size() == 1) {
            String filePath = validPaths.getFirst();
            try {
                return Map.of(filePath, writeTags(filePath, metadata, config));
            } catch (IOException e) {
                LOG.error("Fehler beim Schreiben der Datei {}", filePath, e);
                return Map.of(filePath, WriteResult.failure(null, e.getMessage()));
            }
        }

        Map<String, WriteResult> results = new LinkedHashMap<>();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
            Map<String, StructuredTaskScope.Subtask<WriteResult>> tasks = new LinkedHashMap<>();
            for (String filePath : validPaths) {
                tasks.put(filePath, scope.fork(() -> {
                    try {
                        return writeTags(filePath, metadata, config);
                    } catch (IOException e) {
                        LOG.error("Fehler beim Schreiben der Datei {}", filePath, e);
                        return WriteResult.failure(null, e.getMessage());
                    }
                }));
            }
            scope.join();

            for (var entry : tasks.entrySet()) {
                var subtask = entry.getValue();
                if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    results.put(entry.getKey(), subtask.get());
                } else {
                    LOG.error("Fehler beim Schreiben der Datei {}", entry.getKey(), subtask.exception());
                    results.put(entry.getKey(), WriteResult.failure(null, subtask.exception().getMessage()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Batch-Schreibvorgang unterbrochen");
        }

        return results;
    }

    // ================================
    // INTERNE METHODEN
    // ================================

    private TagFormat determineTargetFormat(Metadata metadata, List<TagInfo> detectedTags,
                                             WriteConfiguration config) {
        if (metadata.getTagFormat() != null && !metadata.getTagFormat().isEmpty()) {
            for (TagFormat f : TagFormat.values()) {
                if (f.getFormatName().equals(metadata.getTagFormat())) {
                    return f;
                }
            }
        }

        // Aus erkannten Tags das passende Format wählen
        for (TagInfo tag : detectedTags) {
            if (writingFactory.getStrategyForFormat(tag.getFormat()) != null) {
                return tag.getFormat();
            }
        }

        // Standard: ID3v2.4
        return config.id3Version() == 3 ? TagFormat.ID3V2_3 : TagFormat.ID3V2_4;
    }

    private TagFormat determineUpdateFormat(List<TagInfo> detectedTags, String key) {
        // Prüfe welche Formate das Feld unterstützen
        for (TagInfo tag : detectedTags) {
            if (tag.getFormat().name().startsWith("ID3")) {
                return tag.getFormat();
            }
        }
        for (TagInfo tag : detectedTags) {
            return tag.getFormat();
        }
        return null;
    }

    private TagInfo findExistingTag(List<TagInfo> detectedTags, TagFormat format) {
        for (TagInfo tag : detectedTags) {
            if (tag.getFormat() == format) {
                return tag;
            }
        }
        // Fallback: Ähnliches Format
        for (TagInfo tag : detectedTags) {
            if (tag.getFormat().name().contains(format.name().split("_")[0])) {
                return tag;
            }
        }
        return null;
    }

    private Metadata readExistingMetadata(SeekableDataSource source, TagInfo tagInfo,
                                           TagFormat format) {
        if (tagInfo == null) return null;
        try {
            TagParsingStrategy parser = parsingFactory.getStrategyForFormat(tagInfo.getFormat());
            if (parser != null) {
                return parser.parseTag(tagInfo.getFormat(), source, tagInfo.getOffset(), tagInfo.getSize());
            }
        } catch (Exception e) {
            LOG.warn("Fehler beim Lesen des bestehenden Tags", e);
        }
        return null;
    }

    private Metadata prepareMetadata(Metadata metadata, SeekableDataSource source,
                                      TagInfo existingTag, WriteConfiguration config) {
        if (config.mode() == WriteMode.CREATE_NEW) {
            return metadata;
        }

        if (config.mode() == WriteMode.UPDATE_EXISTING && existingTag != null) {
            Metadata existing = readExistingMetadata(source, existingTag, existingTag.getFormat());
            if (existing != null) {
                return mergeMetadata(existing, metadata);
            }
        }

        return metadata;
    }

    private Metadata mergeMetadata(Metadata existing, Metadata newMeta) {
        TagFormat format = null;
        for (TagFormat f : TagFormat.values()) {
            if (f.getFormatName().equals(existing.getTagFormat())) {
                format = f;
                break;
            }
        }
        GenericMetadata merged = new GenericMetadata(format);

        // Bestehende Felder
        for (MetadataField<?> field : existing.getFields()) {
            merged.addField(field);
        }

        // Neue Felder (ersetzen)
        for (MetadataField<?> field : newMeta.getFields()) {
            merged.removeField(field.getKey());
            merged.addField(field);
        }

        return merged;
    }
}
