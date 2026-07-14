package com.schwanitz.tagging;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.strategies.detection.context.FormatDetectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;

/**
 * Öffentliche API zur Erkennung von Tag-Formaten in Audiodateien.
 * <p>
 * Bietet verschiedene Eingabequellen für die Tag-Format-Erkennung:
 * <ul>
 *   <li>Dateipfad als Zeichenkette – häufigste Verwendungsweise</li>
 *   <li>{@link Path} – NIO-Pfad-Unterstützung</li>
 *   <li>{@link SeekableDataSource} – Abstraktion über Dateien, Byte-Arrays und Streams</li>
 *   <li>{@code byte[]} – Erkennung im Hauptspeicher</li>
 *   <li>{@link InputStream} – wird intern in eine temporäre Datei gepuffert</li>
 * </ul>
 * <p>
 * Drei Scan-Modi stehen zur Verfügung:
 * <ul>
 *   <li>{@code fullScan} – prüft alle Tag-Formate</li>
 *   <li>{@code comfortScan} – prüft nur wahrscheinliche Formate für den Dateityp</li>
 *   <li>{@code customScan} – prüft nur die angegebenen Formate</li>
 * </ul>
 * <p>
 * Alle Eingabevarianten delegieren intern an {@link SeekableDataSource}, wodurch
 * ein einheitlicher Code-Pfad für die Erkennung verwendet wird.
 * <p>
 * Beispiel:
 * <pre>
 * TagFormatDetector detector = new TagFormatDetector();
 *
 * // Dateipfad
 * List&lt;TagInfo&gt; tags = detector.fullScan("audio.mp3");
 *
 * // Byte-Array
 * List&lt;TagInfo&gt; tags = detector.fullScan(bytes);
 *
 * // InputStream
 * try (SeekableDataSource source = SeekableDataSources.forInputStream(stream, "mp3")) {
 *     List&lt;TagInfo&gt; tags = detector.fullScan(source);
 * }
 * </pre>
 */
public class TagFormatDetector {

    private static final Logger LOG = LoggerFactory.getLogger(TagFormatDetector.class);
    private final FormatDetectionContext detectionContext;

    /**
     * Erstellt einen neuen {@code TagFormatDetector} mit Standard-Erkennungskontext.
     */
    public TagFormatDetector() {
        this.detectionContext = new FormatDetectionContext();
    }

    /**
     * Erstellt einen neuen {@code TagFormatDetector} mit dem angegebenen Erkennungskontext.
     *
     * @param detectionContext der zu verwendende Erkennungskontext, darf nicht {@code null} sein
     * @throws NullPointerException wenn {@code detectionContext} {@code null} ist
     */
    public TagFormatDetector(FormatDetectionContext detectionContext) {
        this.detectionContext = Objects.requireNonNull(detectionContext, "detectionContext must not be null");
    }

    // ================================
    // FULL SCAN METHODS
    // ================================

    /**
     * Führt einen Vollständigen Scan auf einer einzelnen Datei durch.
     * <p>
     * Alle bekannten Tag-Formate werden in Reihenfolge ihrer Wahrscheinlichkeit geprüft.
     *
     * @param filePath der Pfad zur Audiodatei, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Datei auftritt
     * @throws NullPointerException wenn {@code filePath} {@code null} ist
     */
    public List<TagInfo> fullScan(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (SeekableDataSource source = SeekableDataSources.forPath(Path.of(filePath))) {
            return fullScan(source);
        }
    }

    /**
     * Führt einen Vollständigen Scan auf einer einzelnen Datei durch.
     * <p>
     * Alle bekannten Tag-Formate werden in Reihenfolge ihrer Wahrscheinlichkeit geprüft.
     *
     * @param path der NIO-Pfad zur Audiodatei, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Datei auftritt
     * @throws NullPointerException wenn {@code path} {@code null} ist
     */
    public List<TagInfo> fullScan(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try (SeekableDataSource source = SeekableDataSources.forPath(path)) {
            return fullScan(source);
        }
    }

    /**
     * Führt einen Vollständigen Scan auf einem Byte-Array durch.
     * <p>
     * Alle bekannten Tag-Formate werden in Reihenfolge ihrer Wahrscheinlichkeit geprüft.
     *
     * @param data die Audiodaten als Byte-Array, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Daten auftritt
     * @throws NullPointerException wenn {@code data} {@code null} ist
     */
    public List<TagInfo> fullScan(byte[] data) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            return fullScan(source);
        }
    }

    /**
     * Führt einen Vollständigen Scan auf einer Seekable-Datenquelle durch.
     * <p>
     * Alle bekannten Tag-Formate werden in Reihenfolge ihrer Wahrscheinlichkeit geprüft.
     *
     * @param source die Seekable-Datenquelle, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Quelle auftritt
     * @throws NullPointerException wenn {@code source} {@code null} ist
     */
    public List<TagInfo> fullScan(SeekableDataSource source) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        return detectionContext.detectTags(source, ScanConfiguration.fullScan());
    }

    /**
     * Führt einen Vollständigen Scan auf einem InputStream durch.
     * <p>
     * Der Stream wird intern in eine temporäre Datei gepuffert. Alle bekannten Tag-Formate
     * werden in Reihenfolge ihrer Wahrscheinlichkeit geprüft.
     *
     * @param inputStream der Eingabestrom, darf nicht {@code null} sein
     * @param extension   optionale Dateiendung (ohne Punkt) als Hinweis für den Dateityp, z. B. "mp3"
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen des Streams auftritt
     * @throws NullPointerException wenn {@code inputStream} {@code null} ist
     */
    public List<TagInfo> fullScan(InputStream inputStream, String extension) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            return fullScan(source);
        }
    }

    /**
     * Führt einen Vollständigen Scan auf mehreren Dateien durch.
     * <p>
     * Die Dateien werden parallel verarbeitet. Dateien, die nicht gelesen werden können,
     * werden mit einer leeren Liste im Ergebnis aufgeführt.
     *
     * @param filePaths die Pfade zu den Audiodateien, darf nicht {@code null} sein
     * @return eine Zuordnung von Dateipfaden zu Listen von erkannten {@link TagInfo}-Objekten
     * @throws NullPointerException wenn {@code filePaths} {@code null} ist
     */
    public Map<String, List<TagInfo>> fullScan(List<String> filePaths) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.fullScan());
    }

    // ================================
    // COMFORT SCAN METHODS
    // ================================

    /**
     * Führt einen Komfort-Scan auf einer einzelnen Datei durch.
     * <p>
     * Nur die für die Dateiendung wahrscheinlichen Tag-Formate werden geprüft.
     *
     * @param filePath der Pfad zur Audiodatei, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Datei auftritt
     * @throws NullPointerException wenn {@code filePath} {@code null} ist
     */
    public List<TagInfo> comfortScan(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try (SeekableDataSource source = SeekableDataSources.forPath(Path.of(filePath))) {
            return comfortScan(source);
        }
    }

    /**
     * Führt einen Komfort-Scan auf einer einzelnen Datei durch.
     * <p>
     * Nur die für die Dateiendung wahrscheinlichen Tag-Formate werden geprüft.
     *
     * @param path der NIO-Pfad zur Audiodatei, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Datei auftritt
     * @throws NullPointerException wenn {@code path} {@code null} ist
     */
    public List<TagInfo> comfortScan(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try (SeekableDataSource source = SeekableDataSources.forPath(path)) {
            return comfortScan(source);
        }
    }

    /**
     * Führt einen Komfort-Scan auf einem Byte-Array durch.
     * <p>
     * Nur die für den angegebenen Dateityp wahrscheinlichen Tag-Formate werden geprüft.
     *
     * @param data die Audiodaten als Byte-Array, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Daten auftritt
     * @throws NullPointerException wenn {@code data} {@code null} ist
     */
    public List<TagInfo> comfortScan(byte[] data) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            return comfortScan(source);
        }
    }

    /**
     * Führt einen Komfort-Scan auf einer Seekable-Datenquelle durch.
     * <p>
     * Nur die für den Dateityp wahrscheinlichen Tag-Formate werden geprüft.
     *
     * @param source die Seekable-Datenquelle, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Quelle auftritt
     * @throws NullPointerException wenn {@code source} {@code null} ist
     */
    public List<TagInfo> comfortScan(SeekableDataSource source) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        return detectionContext.detectTags(source, ScanConfiguration.comfortScan());
    }

    /**
     * Führt einen Komfort-Scan auf einem InputStream durch.
     * <p>
     * Der Stream wird intern in eine temporäre Datei gepuffert. Nur die für den
     * angegebenen Dateityp wahrscheinlichen Tag-Formate werden geprüft.
     *
     * @param inputStream der Eingabestrom, darf nicht {@code null} sein
     * @param extension   optionale Dateiendung (ohne Punkt) als Hinweis für den Dateityp, z. B. "mp3"
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen des Streams auftritt
     * @throws NullPointerException wenn {@code inputStream} {@code null} ist
     */
    public List<TagInfo> comfortScan(InputStream inputStream, String extension) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            return comfortScan(source);
        }
    }

    /**
     * Führt einen Komfort-Scan auf mehreren Dateien durch.
     * <p>
     * Die Dateien werden parallel verarbeitet. Dateien, die nicht gelesen werden können,
     * werden mit einer leeren Liste im Ergebnis aufgeführt.
     *
     * @param filePaths die Pfade zu den Audiodateien, darf nicht {@code null} sein
     * @return eine Zuordnung von Dateipfaden zu Listen von erkannten {@link TagInfo}-Objekten
     * @throws NullPointerException wenn {@code filePaths} {@code null} ist
     */
    public Map<String, List<TagInfo>> comfortScan(List<String> filePaths) {
        Objects.requireNonNull(filePaths, "filePaths must not be null");
        return detectTagFormats(filePaths, ScanConfiguration.comfortScan());
    }

    // ================================
    // CUSTOM SCAN METHODS
    // ================================

    /**
     * Führt einen benutzerdefinierten Scan auf einer einzelnen Datei durch.
     * <p>
     * Nur die angegebenen Tag-Formate werden geprüft, unabhängig von der Dateiendung.
     *
     * @param filePath der Pfad zur Audiodatei, darf nicht {@code null} sein
     * @param formats  die zu prüfenden Tag-Formate, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Datei auftritt
     * @throws NullPointerException wenn {@code filePath} oder {@code formats} {@code null} ist
     */
    public List<TagInfo> customScan(String filePath, TagFormat... formats) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        try (SeekableDataSource source = SeekableDataSources.forPath(Path.of(filePath))) {
            return customScan(source, formats);
        }
    }

    /**
     * Führt einen benutzerdefinierten Scan auf einer einzelnen Datei durch.
     * <p>
     * Nur die angegebenen Tag-Formate werden geprüft, unabhängig von der Dateiendung.
     *
     * @param path    der NIO-Pfad zur Audiodatei, darf nicht {@code null} sein
     * @param formats die zu prüfenden Tag-Formate, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Datei auftritt
     * @throws NullPointerException wenn {@code path} oder {@code formats} {@code null} ist
     */
    public List<TagInfo> customScan(Path path, TagFormat... formats) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        try (SeekableDataSource source = SeekableDataSources.forPath(path)) {
            return customScan(source, formats);
        }
    }

    /**
     * Führt einen benutzerdefinierten Scan auf einem Byte-Array durch.
     * <p>
     * Nur die angegebenen Tag-Formate werden geprüft, unabhängig vom Dateityp.
     *
     * @param data    die Audiodaten als Byte-Array, darf nicht {@code null} sein
     * @param formats die zu prüfenden Tag-Formate, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Daten auftritt
     * @throws NullPointerException wenn {@code data} oder {@code formats} {@code null} ist
     */
    public List<TagInfo> customScan(byte[] data, TagFormat... formats) throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        try (SeekableDataSource source = SeekableDataSources.forBytes(data)) {
            return customScan(source, formats);
        }
    }

    /**
     * Führt einen benutzerdefinierten Scan auf einer Seekable-Datenquelle durch.
     * <p>
     * Nur die angegebenen Tag-Formate werden geprüft, unabhängig vom Dateityp.
     *
     * @param source  die Seekable-Datenquelle, darf nicht {@code null} sein
     * @param formats die zu prüfenden Tag-Formate, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen der Quelle auftritt
     * @throws NullPointerException wenn {@code source} oder {@code formats} {@code null} ist
     */
    public List<TagInfo> customScan(SeekableDataSource source, TagFormat... formats) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        return detectionContext.detectTags(source, ScanConfiguration.customScan(formats));
    }

    /**
     * Führt einen benutzerdefinierten Scan auf einem InputStream durch.
     * <p>
     * Der Stream wird intern in eine temporäre Datei gepuffert. Nur die angegebenen
     * Tag-Formate werden geprüft.
     *
     * @param inputStream der Eingabestrom, darf nicht {@code null} sein
     * @param extension   optionale Dateiendung (ohne Punkt) als Hinweis für den Dateityp, z. B. "mp3"
     * @param formats     die zu prüfenden Tag-Formate, darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte, niemals {@code null}
     * @throws IOException wenn ein E/A-Fehler beim Lesen des Streams auftritt
     * @throws NullPointerException wenn {@code inputStream} oder {@code formats} {@code null} ist
     */
    public List<TagInfo> customScan(InputStream inputStream, String extension, TagFormat... formats) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(formats, "formats must not be null");
        try (SeekableDataSource source = SeekableDataSources.forInputStream(inputStream, extension)) {
            return customScan(source, formats);
        }
    }

    /**
     * Führt einen benutzerdefinierten Scan auf mehreren Dateien durch.
     * <p>
     * Die Dateien werden parallel verarbeitet. Dateien, die nicht gelesen werden können,
     * werden mit einer leeren Liste im Ergebnis aufgeführt.
     *
     * @param filePaths die Pfade zu den Audiodateien, darf nicht {@code null} sein
     * @param formats   die zu prüfenden Tag-Formate, darf nicht {@code null} sein
     * @return eine Zuordnung von Dateipfaden zu Listen von erkannten {@link TagInfo}-Objekten
     * @throws NullPointerException wenn {@code filePaths} oder {@code formats} {@code null} ist
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
     * Erkennt Tag-Formate in mehreren Dateien parallel.
     * <p>
     * Verwendet {@link StructuredTaskScope} zur parallelen Verarbeitung.
     * Bei Einzeldateien wird die Verarbeitung sequenziell durchgeführt.
     * Dateien, die nicht verarbeitet werden können, werden mit einer leeren
     * {@link TagInfo}-Liste im Ergebnis erfasst.
     *
     * @param filePaths die Liste der Dateipfade, {@code null}-Einträge werden gefiltert
     * @param config    die Scan-Konfiguration
     * @return eine Zuordnung von Dateipfaden zu Listen von erkannten {@link TagInfo}-Objekten
     */
    private Map<String, List<TagInfo>> detectTagFormats(List<String> filePaths, ScanConfiguration config) {
        Map<String, List<TagInfo>> results = new LinkedHashMap<>();
        List<String> validPaths = filePaths.stream()
                .filter(Objects::nonNull)
                .toList();

        if (validPaths.isEmpty()) {
            return results;
        }

        if (validPaths.size() == 1) {
            String filePath = validPaths.getFirst();
            try (SeekableDataSource source = SeekableDataSources.forPath(Path.of(filePath))) {
                results.put(filePath, detectionContext.detectTags(source, config));
            } catch (IOException e) {
                LOG.error("Error processing file {}", filePath, e);
                results.put(filePath, List.of());
            }
            return results;
        }

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
            Map<String, StructuredTaskScope.Subtask<List<TagInfo>>> tasks = new LinkedHashMap<>();
            for (String filePath : validPaths) {
                tasks.put(filePath, scope.fork(() -> {
                    try (SeekableDataSource source = SeekableDataSources.forPath(Path.of(filePath))) {
                        return detectionContext.detectTags(source, config);
                    } catch (IOException e) {
                        LOG.error("Error processing file {}", filePath, e);
                        return List.<TagInfo>of();
                    }
                }));
            }
            scope.join();

            for (var entry : tasks.entrySet()) {
                var subtask = entry.getValue();
                if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    results.put(entry.getKey(), subtask.get());
                } else {
                    LOG.error("Error processing file {}", entry.getKey(), subtask.exception());
                    results.put(entry.getKey(), List.of());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Batch processing interrupted");
        }

        return results;
    }
}