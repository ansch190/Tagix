package com.schwanitz.api;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;

/**
 * Zentrale API zur Erkennung und Analyse von Metadaten in Audiodateien.
 * <p>
 * Verbindet Tag-Erkennung und Tag-Parsing in einer vollständigen Pipeline.
 * Unterstützt verschiedene Eingabequellen: Dateipfade, NIO-Pfade, Byte-Arrays,
 * Input-Streams und {@link SeekableDataSource}-Objekte.
 * <p>
 * Bietet zwei Konstruktoren: einen Standardkonstruktor, der einen neuen
 * {@link TagFormatDetector} erstellt, und einen Konstruktor zur Injektion
 * eines benutzerdefinierten Detektors.
 */
public class MetadataManager {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataManager.class);

    private final TagFormatDetector detector;

    /**
     * Erstellt einen neuen {@code MetadataManager} mit einem Standard-{@link TagFormatDetector}.
     */
    public MetadataManager() {
        this.detector = new TagFormatDetector();
    }

    /**
     * Erstellt einen neuen {@code MetadataManager} mit dem angegebenen {@link TagFormatDetector}.
     *
     * @param detector der zu verwendende TagFormatDetector; darf nicht {@code null} sein
     * @throws NullPointerException wenn {@code detector} null ist
     */
    public MetadataManager(TagFormatDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector must not be null");
    }

    // ================================
    // FILE PATH METHODS
    // ================================

    /**
     * Liest alle Metadaten aus einer Datei im Vollscan-Modus.
     * <p>
     * Verwendet die Standardkonfiguration {@link ScanConfiguration#fullScan()},
     * die alle unterstützten Tag-Formate erkennt.
     *
     * @param filePath der Dateipfad der zu lesenden Audiodatei
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Datei nicht gelesen werden kann
     * @throws NullPointerException wenn {@code filePath} null ist
     */
    public List<Metadata> readFromFile(String filePath) throws IOException {
        return readFromFile(filePath, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus einer Datei mit der angegebenen Scan-Konfiguration.
     * <p>
     * Abhängig vom Scan-Modus der Konfiguration wird eine der Erkennungsmethoden
     * des {@link TagFormatDetector} aufgerufen:
     * <ul>
     *   <li>{@code FULL_SCAN} – erkennt alle unterstützten Tag-Formate</li>
     *   <li>{@code COMFORT_SCAN} – erkennt nur die gängigsten Tag-Formate</li>
     *   <li>{@code CUSTOM_SCAN} – erkennt nur die in der Konfiguration angegebenen Formate</li>
     * </ul>
     *
     * @param filePath der Dateipfad der zu lesenden Audiodatei
     * @param config   die Scan-Konfiguration, die den Erkennungsmodus und ggf. benutzerdefinierte Formate bestimmt
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Datei nicht gelesen werden kann
     * @throws NullPointerException wenn {@code filePath} oder {@code config} null ist
     */
    public List<Metadata> readFromFile(String filePath, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(config, "config must not be null");
        List<TagInfo> detectedTags = switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(filePath);
            case COMFORT_SCAN -> detector.comfortScan(filePath);
            case CUSTOM_SCAN -> detector.customScan(filePath, config.getCustomFormats().toArray(new TagFormat[0]));
        };
        return parseTags(filePath, detectedTags);
    }

    /**
     * Liest alle Metadaten aus einer Datei anhand eines NIO-Pfads im Vollscan-Modus.
     * <p>
     * Verwendet die Standardkonfiguration {@link ScanConfiguration#fullScan()},
     * die alle unterstützten Tag-Formate erkennt.
     *
     * @param path der NIO-Pfad zur Audiodatei
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Datei nicht gelesen werden kann
     * @throws NullPointerException wenn {@code path} null ist
     */
    public List<Metadata> readFromFile(Path path) throws IOException {
        return readFromFile(path, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus einer Datei anhand eines NIO-Pfads mit der angegebenen Scan-Konfiguration.
     *
     * @param path   der NIO-Pfad zur Audiodatei
     * @param config die Scan-Konfiguration, die den Erkennungsmodus bestimmt
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Datei nicht gelesen werden kann
     * @throws NullPointerException wenn {@code path} oder {@code config} null ist
     * @see #readFromFile(String, ScanConfiguration)
     */
    public List<Metadata> readFromFile(Path path, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(config, "config must not be null");
        List<TagInfo> detectedTags = switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(path);
            case COMFORT_SCAN -> detector.comfortScan(path);
            case CUSTOM_SCAN -> detector.customScan(path, config.getCustomFormats().toArray(new TagFormat[0]));
        };
        return parseTags(path.toString(), detectedTags);
    }

    // ================================
    // BYTE ARRAY METHODS
    // ================================

    /**
     * Liest alle Metadaten aus einem Byte-Array im Vollscan-Modus.
     * <p>
     * Erstellt eine {@link SeekableDataSource} aus dem Byte-Array und führt
     * die Erkennung und das Parsing durch. Die Ressource wird automatisch geschlossen.
     *
     * @param data das Byte-Array mit den Audiodaten
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Daten nicht gelesen werden können
     * @throws NullPointerException wenn {@code data} null ist
     */
    public List<Metadata> readFromBytes(byte[] data) throws IOException {
        return readFromBytes(data, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus einem Byte-Array mit der angegebenen Scan-Konfiguration.
     * <p>
     * Erstellt eine {@link SeekableDataSource} aus dem Byte-Array und führt
     * die Erkennung und das Parsing durch. Die Ressource wird automatisch geschlossen.
     *
     * @param data   das Byte-Array mit den Audiodaten
     * @param config die Scan-Konfiguration, die den Erkennungsmodus bestimmt
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Daten nicht gelesen werden können
     * @throws NullPointerException wenn {@code data} oder {@code config} null ist
     */
    public List<Metadata> readFromBytes(byte[] data, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(config, "config must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            List<TagInfo> detectedTags = detectFromSource(source, config);
            return parseTagsFromSource(source, detectedTags);
        }
    }

    // ================================
    // INPUT STREAM METHODS
    // ================================

    /**
     * Liest alle Metadaten aus einem InputStream im Vollscan-Modus.
     * <p>
     * Der Stream wird in eine temporäre Datei gepuffert, um wahlfreien Zugriff zu ermöglichen.
     * Die temporäre Datei wird nach der Verarbeitung automatisch gelöscht.
     *
     * @param inputStream der Eingabestrom, aus dem gelesen wird
     * @param extension   optionaler Dateiendungs-Hinweis (ohne Punkt), z. B. "mp3"
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn der Stream nicht gelesen werden kann oder ein I/O-Fehler auftritt
     * @throws NullPointerException wenn {@code inputStream} null ist
     */
    public List<Metadata> readFromInputStream(InputStream inputStream, String extension) throws IOException {
        return readFromInputStream(inputStream, extension, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus einem InputStream mit der angegebenen Scan-Konfiguration.
     * <p>
     * Der Stream wird in eine temporäre Datei gepuffert, um wahlfreien Zugriff zu ermöglichen.
     * Die temporäre Datei wird nach der Verarbeitung automatisch gelöscht.
     *
     * @param inputStream der Eingabestrom, aus dem gelesen wird
     * @param extension   optionaler Dateiendungs-Hinweis (ohne Punkt), z. B. "mp3"
     * @param config      die Scan-Konfiguration, die den Erkennungsmodus bestimmt
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn der Stream nicht gelesen werden kann oder ein I/O-Fehler auftritt
     * @throws NullPointerException wenn {@code inputStream} oder {@code config} null ist
     */
    public List<Metadata> readFromInputStream(InputStream inputStream, String extension, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(config, "config must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            List<TagInfo> detectedTags = detectFromSource(source, config);
            return parseTagsFromSource(source, detectedTags);
        }
    }

    // ================================
    // SEEKABLE DATA SOURCE METHODS
    // ================================

    /**
     * Liest alle Metadaten aus einer {@link SeekableDataSource} im Vollscan-Modus.
     *
     * @param source die Datenquelle mit wahlfreiem Zugriff
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Datenquelle nicht gelesen werden kann
     * @throws NullPointerException wenn {@code source} null ist
     */
    public List<Metadata> readFromSource(SeekableDataSource source) throws IOException {
        return readFromSource(source, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus einer {@link SeekableDataSource} mit der angegebenen Scan-Konfiguration.
     *
     * @param source die Datenquelle mit wahlfreiem Zugriff
     * @param config die Scan-Konfiguration, die den Erkennungsmodus bestimmt
     * @return eine Liste der erkannten {@link Metadata}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Datenquelle nicht gelesen werden kann
     * @throws NullPointerException wenn {@code source} oder {@code config} null ist
     */
    public List<Metadata> readFromSource(SeekableDataSource source, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(config, "config must not be null");
        List<TagInfo> detectedTags = detectFromSource(source, config);
        return parseTagsFromSource(source, detectedTags);
    }

    // ================================
    // BATCH METHODS
    // ================================

    /**
     * Liest alle Metadaten aus mehreren Dateien im Vollscan-Modus.
     * <p>
     * Verwendet die Standardkonfiguration {@link ScanConfiguration#fullScan()}.
     *
     * @param filePaths die Liste der Dateipfade, die verarbeitet werden sollen
     * @return eine Zuordnung von Dateipfaden zu den jeweiligen erkannten {@link Metadata}-Listen
     * @throws NullPointerException wenn {@code filePaths} null ist
     */
    public Map<String, List<Metadata>> readFromFiles(List<String> filePaths) {
        return readFromFiles(filePaths, ScanConfiguration.fullScan());
    }

    /**
     * Liest Metadaten aus mehreren Dateien mit der angegebenen Scan-Konfiguration.
     * <p>
     * Verwendet virtuelle Threads und Structured Concurrency zur parallelen Verarbeitung.
     * Für jede Datei wird ein eigener virtueller Thread gestartet, sodass Erkennung
     * und Parsing aller Dateien gleichzeitig ausgeführt werden.
     * <p>
     * Bei nur einer Datei wird die Verarbeitung direkt im aktuellen Thread durchgeführt,
     * ohne Thread-Erstellung. Fehlerhafte Dateien werden mit einer leeren Metadaten-Liste
     * im Ergebnis aufgeführt und im Protokoll erfasst.
     * <p>
     * Die Reihenfolge der Ergebnisse in der zurückgegebenen Map entspricht der Reihenfolge
     * der Eingabepfade (einschließlich null-Filterung).
     *
     * @param filePaths die Liste der Dateipfade, die verarbeitet werden sollen; {@code null}-Einträge werden gefiltert
     * @param config    die Scan-Konfiguration, die den Erkennungsmodus bestimmt
     * @return eine Zuordnung von Dateipfaden zu den jeweiligen erkannten {@link Metadata}-Listen;
     *         leere Map, wenn die Eingabe leer ist oder nur {@code null}-Einträge enthält
     * @throws NullPointerException wenn {@code filePaths} oder {@code config} null ist
     */
    public Map<String, List<Metadata>> readFromFiles(List<String> filePaths, ScanConfiguration config) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        Objects.requireNonNull(config, "config must not be null");
        List<String> validPaths = filePaths.stream().filter(Objects::nonNull).toList();
        if (validPaths.isEmpty()) {
            return Map.of();
        }

        if (validPaths.size() == 1) {
            String filePath = validPaths.getFirst();
            try {
                List<TagInfo> detected = detectFromFile(filePath, config);
                return Map.of(filePath, parseTags(filePath, detected));
            } catch (IOException e) {
                LOG.error("Error processing file {}: {}", filePath, e.getMessage());
                return Map.of(filePath, List.of());
            }
        }

        Map<String, List<Metadata>> results = new LinkedHashMap<>();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
            Map<String, StructuredTaskScope.Subtask<List<Metadata>>> tasks = new LinkedHashMap<>();
            for (String filePath : validPaths) {
                tasks.put(filePath, scope.fork(() -> {
                    try {
                        List<TagInfo> detected = detectFromFile(filePath, config);
                        return parseTags(filePath, detected);
                    } catch (IOException e) {
                        LOG.error("Error processing file {}: {}", filePath, e.getMessage());
                        return List.<Metadata>of();
                    }
                }));
            }
            scope.join();

            for (var entry : tasks.entrySet()) {
                var subtask = entry.getValue();
                if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    results.put(entry.getKey(), subtask.get());
                } else {
                    LOG.error("Error processing file {}: {}", entry.getKey(), subtask.exception());
                    results.put(entry.getKey(), List.of());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Batch processing interrupted");
        }

        return results;
    }

    // ================================
    // INTERNAL METHODS
    // ================================

    private List<TagInfo> detectFromSource(SeekableDataSource source, ScanConfiguration config) throws IOException {
        return switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(source);
            case COMFORT_SCAN -> detector.comfortScan(source);
            case CUSTOM_SCAN -> detector.customScan(source, config.getCustomFormats().toArray(new TagFormat[0]));
        };
    }

    private List<TagInfo> detectFromFile(String filePath, ScanConfiguration config) throws IOException {
        return switch (config.getMode()) {
            case FULL_SCAN -> detector.fullScan(filePath);
            case COMFORT_SCAN -> detector.comfortScan(filePath);
            case CUSTOM_SCAN -> detector.customScan(filePath, config.getCustomFormats().toArray(new TagFormat[0]));
        };
    }

    private List<Metadata> parseTags(String filePath, List<TagInfo> tagInfos) {
        List<Metadata> metadataList = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            for (TagInfo tagInfo : tagInfos) {
                TagParsingStrategy strategy = TagParsingStrategyFactory.getStrategyForFormat(tagInfo.getFormat());
                if (strategy != null) {
                    try {
                        Metadata metadata = strategy.parseTag(
                                tagInfo.getFormat(), raf, tagInfo.getOffset(), tagInfo.getSize());
                        metadataList.add(metadata);
                    } catch (IOException e) {
                        LOG.warn("Error parsing tag {} in {}: {}", tagInfo.getFormat(), filePath, e.getMessage());
                    }
                } else {
                    LOG.debug("No parser available for format: {}", tagInfo.getFormat());
                }
            }
        } catch (IOException e) {
            LOG.error("Cannot read file {}: {}", filePath, e.getMessage());
        }

        return metadataList;
    }

    private List<Metadata> parseTagsFromSource(SeekableDataSource source, List<TagInfo> tagInfos) {
        List<Metadata> metadataList = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(source.name(), "r")) {
            for (TagInfo tagInfo : tagInfos) {
                TagParsingStrategy strategy = TagParsingStrategyFactory.getStrategyForFormat(tagInfo.getFormat());
                if (strategy != null) {
                    try {
                        Metadata metadata = strategy.parseTag(
                                tagInfo.getFormat(), raf, tagInfo.getOffset(), tagInfo.getSize());
                        metadataList.add(metadata);
                    } catch (IOException e) {
                        LOG.warn("Error parsing tag {} in {}: {}", tagInfo.getFormat(), source.name(), e.getMessage());
                    }
                } else {
                    LOG.debug("No parser available for format: {}", tagInfo.getFormat());
                }
            }
        } catch (IOException e) {
            LOG.error("Cannot read source {}: {}", source.name(), e.getMessage());
        }

        return metadataList;
    }
}