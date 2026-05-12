package com.schwanitz.strategies.detection.context;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.factory.TagDetectionStrategyFactory;
import com.schwanitz.tagging.FormatPriorityManager;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Orchestriert die Erkennung von Tag-Formaten anhand einer Menge von Erkennungsstrategien.
 * <p>
 * Diese Klasse fungiert als Kontext im Strategy-Pattern und koordiniert den
 * Erkennungsprozess mithilfe der verfügbaren {@link TagDetectionStrategy}-Instanzen.
 * Sie akzeptiert ausschließlich {@link SeekableDataSource}-Instanzen als Eingabe,
 * die durch Dateien, Byte-Arrays oder gepufferte Ströme unterstützt werden.
 * <p>
 * Der Erkennungsablauf für jede Datei:
 * <ol>
 *   <li>Dateipuffer lesen (Anfang und Ende der Datenquelle)</li>
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
     * Erkennt Tags in einer Seekable-Datenquelle ({@link SeekableDataSource}).
     * <p>
     * Puffer werden aus der Datenquelle gelesen und die Erkennung wird
     * mit den anhand der Dateiendung und Scan-Konfiguration ermittelten Formaten durchgeführt.
     *
     * @param source die Seekable-Datenquelle; darf nicht {@code null} sein
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

        List<TagDetectionStrategy> strategies = TagDetectionStrategyFactory.getStrategiesForFormats(formatsToCheck);
        if (strategies.isEmpty()) {
            LOG.debug("No strategies found for requested formats");
            return List.of();
        }

        List<TagInfo> detectedTags = performDetection(strategies, formatsToCheck, source, buffers);

        LOG.info("Detected {} tags in {} using {}", detectedTags.size(), source.name(), config.getMode());
        return detectedTags;
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
     * @param source           die {@link SeekableDataSource} für Lesezugriffe
     * @param buffers          die gelesenen Dateipuffer
     * @return eine Liste der erkannten und gefilterten {@link TagInfo}-Objekte
     * @throws IOException wenn ein Fehler beim Lesen der Datenquelle auftritt
     */
    private List<TagInfo> performDetection(List<TagDetectionStrategy> strategies,
                                           List<TagFormat> requestedFormats,
                                           SeekableDataSource source,
                                           FileBuffers buffers) throws IOException {

        List<TagInfo> detectedTags = new ArrayList<>();

        LOG.debug("Using {} unique strategies for {} formats", strategies.size(), requestedFormats.size());

        for (TagDetectionStrategy strategy : strategies) {
            try {
                if (strategy.canDetect(buffers.startBuffer, buffers.endBuffer)) {
                    List<TagInfo> strategyTags = strategy.detectTags(source,
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