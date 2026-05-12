package com.schwanitz.strategies.detection.context;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import com.schwanitz.strategies.detection.factory.TagDetectionStrategyFactory;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.FormatPriorityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Orchestriert die Erkennung von Tag-Formaten anhand einer Menge von Erkennungsstrategien.
 * <p>
 * Diese Klasse fungiert als Kontext im Strategy-Pattern und koordiniert den
 * Erkennungsprozess mithilfe der verfügbaren {@link TagDetectionStrategy}-Instanzen.
 * Sie unterstützt zwei Eingabequellen:
 * <ul>
 *   <li>Dateipfade – direkter Zugriff über {@link RandomAccessFile}</li>
 *   <li>{@link SeekableDataSource}-Instanzen – abstrakte Datenquellen, die
 *       durch Dateien, Byte-Arrays oder gepufferte Ströme unterstützt werden</li>
 * </ul>
 * <p>
 * Der Erkennungsablauf für jede Datei:
 * <ol>
 *   <li>Dateipuffer lesen (Anfang und Ende der Datei)</li>
 *   <li>Zuständige Formate anhand der Scan-Konfiguration bestimmen</li>
 *   <li>Passende Strategien über die {@link TagDetectionStrategyFactory} ermitteln</li>
 *   <li>Jede Strategie in Phase 1 ({@link TagDetectionStrategy#canDetect}) prüfen</li>
 *   <li>Bei positiver Signaturerkennung Phase 2 ({@link TagDetectionStrategy#detectTags}) durchführen</li>
 *   <li>Gefundene Tags nach angeforderten Formaten filtern und zurückgeben</li>
 * </ol>
 *
 * @see TagDetectionStrategy
 * @see TagDetectionStrategyFactory
 * @see ScanConfiguration
 */
public class FormatDetectionContext {

    private static final Logger LOG = LoggerFactory.getLogger(FormatDetectionContext.class);
    private static final int BUFFER_SIZE = 4096;

    /**
     * Erkennt Tags in einer Datei anhand des angegebenen Dateipfads.
     * <p>
     * Die Datei wird geöffnet, Puffer werden gelesen und die Erkennung wird
     * mit den anhand der Dateiendung und Scan-Konfiguration ermittelten Formaten durchgeführt.
     *
     * @param filePath der Dateipfad; darf nicht {@code null} sein
     * @param config   die Scan-Konfiguration; darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn die Datei nicht existiert, nicht lesbar ist oder ein Lesefehler auftritt
     */
    public List<TagInfo> detectTags(String filePath, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(config, "config must not be null");

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            throw new IOException("Datei existiert nicht oder ist nicht lesbar: " + filePath);
        }

        if (file.length() == 0) {
            LOG.debug("Skipping empty file: {}", filePath);
            return List.of();
        }

        String extension = getFileExtension(filePath);
        List<TagFormat> formatsToCheck = getFormatsToCheck(config, extension);

        if (formatsToCheck == null || formatsToCheck.isEmpty()) {
            LOG.debug("No formats to check for file: {}", filePath);
            return List.of();
        }

        LOG.debug("Start Tag-Detection with Mode: {} for File: {}", config.getMode(), filePath);

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            FileBuffers buffers = readFileBuffers(raf);

            List<TagDetectionStrategy> strategies = TagDetectionStrategyFactory.getStrategiesForFormats(formatsToCheck);
            if (strategies.isEmpty()) {
                LOG.debug("No strategies found for requested formats");
                return List.of();
            }

            List<TagInfo> detectedTags = performDetection(strategies, formatsToCheck, raf, filePath, buffers);

            LOG.info("Detected {} tags in {} using {}", detectedTags.size(), filePath, config.getMode());
            return detectedTags;
        }
    }

    /**
     * Erkennt Tags in einer Suchdatenquelle ({@link SeekableDataSource}).
     * <p>
     * Für dateigestützte Datenquellen wird die Datei direkt verwendet.
     * Für In-Memory-Datenquellen wird eine temporäre Datei erstellt, um
     * RandomAccessFile-basierte Erkennungsstrategien zu unterstützen.
     *
     * @param source die Suchdatenquelle; darf nicht {@code null} sein
     * @param config die Scan-Konfiguration; darf nicht {@code null} sein
     * @return eine Liste der erkannten {@link TagInfo}-Objekte; leer, wenn keine Tags gefunden wurden
     * @throws IOException wenn ein Fehler beim Lesen der Datenquelle auftritt
     */
    public List<TagInfo> detectTags(SeekableDataSource source, ScanConfiguration config) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(config, "config must not be null");

        long sourceLength = source.length();
        if (sourceLength == 0) {
            LOG.debug("Skipping empty data source: {}", source.name());
            return List.of();
        }

        String extension = getFileExtension(source.name());
        List<TagFormat> formatsToCheck = getFormatsToCheck(config, extension);

        if (formatsToCheck == null || formatsToCheck.isEmpty()) {
            LOG.debug("No formats to check for source: {}", source.name());
            return List.of();
        }

        LOG.debug("Start Tag-Detection with Mode: {} for source: {}", config.getMode(), source.name());

        FileBuffers buffers = readSourceBuffers(source);

        // For detection strategies that need RandomAccessFile, we create a temporary file
        // from the data source if it's not already file-backed
        try (RandomAccessFile raf = createRandomAccessFile(source)) {
            List<TagDetectionStrategy> strategies = TagDetectionStrategyFactory.getStrategiesForFormats(formatsToCheck);
            if (strategies.isEmpty()) {
                LOG.debug("No strategies found for requested formats");
                return List.of();
            }

            List<TagInfo> detectedTags = performDetection(strategies, formatsToCheck, raf, source.name(), buffers);

            LOG.info("Detected {} tags in {} using {}", detectedTags.size(), source.name(), config.getMode());
            return detectedTags;
        }
    }

    /**
     * Führt die eigentliche Erkennung mithilfe der angegebenen Strategien durch.
     * <p>
     * Für jede Strategie wird zuerst {@link TagDetectionStrategy#canDetect} und
     * bei Erfolg {@link TagDetectionStrategy#detectTags} aufgerufen. Die Ergebnisse
     * werden nach den angeforderten Formaten gefiltert.
     *
     * @param strategies       die Liste der zu prüfenden Erkennungsstrategien
     * @param requestedFormats die angeforderten Tag-Formate zum Filtern der Ergebnisse
     * @param raf              die geöffnete {@link RandomAccessFile}
     * @param filePath         der Dateipfad zur Protokollierung
     * @param buffers          die gelesenen Dateipuffer
     * @return eine Liste der erkannten und gefilterten {@link TagInfo}-Objekte
     * @throws IOException wenn ein Fehler beim Lesen der Datei auftritt
     */
    private List<TagInfo> performDetection(List<TagDetectionStrategy> strategies,
                                           List<TagFormat> requestedFormats,
                                           RandomAccessFile raf, String filePath,
                                           FileBuffers buffers) throws IOException {

        List<TagInfo> detectedTags = new ArrayList<>();

        LOG.debug("Using {} unique strategies for {} formats", strategies.size(), requestedFormats.size());

        for (TagDetectionStrategy strategy : strategies) {
            try {
                if (strategy.canDetect(buffers.startBuffer, buffers.endBuffer)) {
                    List<TagInfo> strategyTags = strategy.detectTags(raf, filePath,
                            buffers.startBuffer, buffers.endBuffer);

                    if (strategyTags != null) {
                        List<TagInfo> filteredTags = strategyTags.stream()
                                .filter(tag -> tag != null && requestedFormats.contains(tag.getFormat()))
                                .toList();

                        detectedTags.addAll(filteredTags);

                        LOG.debug("Strategy {} found {} matching tags",
                                strategy.getClass().getSimpleName(), filteredTags.size());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error in strategy {}: {}", strategy.getClass().getSimpleName(), e.getMessage());
            }
        }

        return detectedTags;
    }

    /**
     * Erstellt eine {@link RandomAccessFile} aus einer {@link SeekableDataSource}.
     * <p>
     * Bei In-Memory-Datenquellen wird der Inhalt in eine temporäre Datei geschrieben,
     * die für RandomAccessFile-basierte Erkennungsstrategien benötigt wird.
     *
     * @param source die Suchdatenquelle
     * @return eine geöffnete {@link RandomAccessFile} im Lesemodus
     * @throws IOException wenn ein Fehler beim Schreiben der temporären Datei auftritt
     */
    private RandomAccessFile createRandomAccessFile(SeekableDataSource source) throws IOException {
        byte[] data = source.readAll();
        Path tempFile = Files.createTempFile("tagix-detect", ".tmp");
        Files.write(tempFile, data);
        return new RandomAccessFile(tempFile.toFile(), "r");
    }

    /**
     * Liest Anfangs- und Endpuffer aus einer geöffneten {@link RandomAccessFile}.
     *
     * @param raf die geöffnete Datei
     * @return ein {@link FileBuffers}-Record mit Start- und Endpuffer
     * @throws IOException wenn ein Fehler beim Lesen auftritt
     */
    private FileBuffers readFileBuffers(RandomAccessFile raf) throws IOException {
        byte[] startBuffer = new byte[BUFFER_SIZE];
        byte[] endBuffer = new byte[BUFFER_SIZE];

        raf.seek(0);
        int startRead = raf.read(startBuffer);

        long endPosition = Math.max(0, raf.length() - BUFFER_SIZE);
        raf.seek(endPosition);
        raf.read(endBuffer);

        if (raf.length() < BUFFER_SIZE) {
            byte[] actualStartBuffer = new byte[startRead];
            System.arraycopy(startBuffer, 0, actualStartBuffer, 0, startRead);
            startBuffer = actualStartBuffer;
            endBuffer = actualStartBuffer;
        }

        return new FileBuffers(startBuffer, endBuffer);
    }

    /**
     * Liest Anfangs- und Endpuffer aus einer {@link SeekableDataSource}.
     *
     * @param source die Suchdatenquelle
     * @return ein {@link FileBuffers}-Record mit Start- und Endpuffer
     * @throws IOException wenn ein Fehler beim Lesen auftritt
     */
    private FileBuffers readSourceBuffers(SeekableDataSource source) throws IOException {
        long sourceLength = source.length();
        int bufferSize = (int) Math.min(BUFFER_SIZE, sourceLength);

        byte[] startBuffer = new byte[bufferSize];
        int startRead = source.read(0, startBuffer, 0, bufferSize);
        if (startRead < bufferSize) {
            byte[] trimmed = new byte[Math.max(startRead, 0)];
            System.arraycopy(startBuffer, 0, trimmed, 0, Math.max(startRead, 0));
            startBuffer = trimmed;
            return new FileBuffers(startBuffer, startBuffer);
        }

        byte[] endBuffer;
        if (sourceLength <= BUFFER_SIZE) {
            endBuffer = startBuffer;
        } else {
            long endPosition = sourceLength - BUFFER_SIZE;
            endBuffer = new byte[BUFFER_SIZE];
            int endRead = source.read(endPosition, endBuffer, 0, BUFFER_SIZE);
            if (endRead < BUFFER_SIZE) {
                byte[] trimmed = new byte[Math.max(endRead, 0)];
                System.arraycopy(endBuffer, 0, trimmed, 0, Math.max(endRead, 0));
                endBuffer = trimmed;
            }
        }

        return new FileBuffers(startBuffer, endBuffer);
    }

    /**
     * Extrahiert die Dateiendung aus einem Dateipfad oder Dateinamen.
     * <p>
     * Entfernt auch Query-Strings und Fragmente aus URLs.
     *
     * @param filePath der Dateipfad oder Dateiname
     * @return die Dateiendung in Kleinbuchstaben, oder ein leerer String wenn keine vorhanden ist
     */
    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex == -1) return "";
        // Strip query strings or fragments from URLs
        String ext = filePath.substring(dotIndex + 1).toLowerCase();
        int queryIdx = ext.indexOf('?');
        if (queryIdx >= 0) ext = ext.substring(0, queryIdx);
        int hashIdx = ext.indexOf('#');
        if (hashIdx >= 0) ext = ext.substring(0, hashIdx);
        return ext;
    }

    /**
     * Bestimmt die zu prüfenden Tag-Formate anhand der Scan-Konfiguration und Dateiendung.
     *
     * @param config    die Scan-Konfiguration
     * @param extension die Dateiendung in Kleinbuchstaben
     * @return die Liste der zu prüfenden {@link TagFormat}-Werte
     */
    private List<TagFormat> getFormatsToCheck(ScanConfiguration config, String extension) {
        return switch (config.getMode()) {
            case FULL_SCAN -> FormatPriorityManager.getFullScanPriority();
            case COMFORT_SCAN -> FormatPriorityManager.getComfortScanPriority(extension);
            case CUSTOM_SCAN -> config.getCustomFormats();
        };
    }

    /**
 * Interner Record zum Halten der gelesenen Dateipuffer (Anfang und Ende).
 *
 * @param startBuffer Puffer mit den ersten Bytes der Datei
 * @param endBuffer   Puffer mit den letzten Bytes der Datei
 */
    private record FileBuffers(byte[] startBuffer, byte[] endBuffer) {}
}