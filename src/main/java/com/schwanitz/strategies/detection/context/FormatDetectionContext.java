package com.schwanitz.strategies.detection.context;

import com.schwanitz.strategies.detection.*;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.FormatPriorityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * FormatDetectionContext übernimmt jetzt die komplette Detection-Logik
 * <p>
 * Verwaltet Datei-I/O, Buffer-Management und Strategy-Koordination
 */
public class FormatDetectionContext {

    private static final Logger Log = LoggerFactory.getLogger(FormatDetectionContext.class);
    private static final int BUFFER_SIZE = 4096; // 4 KB Puffer

    // Strategy instances (created once, reused)
    private final ID3V1DetectionStrategy id3v1Strategy = new ID3V1DetectionStrategy();
    private final ID3V2DetectionStrategy id3v2Strategy = new ID3V2DetectionStrategy();
    private final APEDetectionStrategy apeStrategy = new APEDetectionStrategy();
    private final Lyrics3DetectionStrategy lyrics3Strategy = new Lyrics3DetectionStrategy();
    private final WAVDetectionStrategy wavStrategy = new WAVDetectionStrategy();
    private final VorbisCommentDetectionStrategy vorbisStrategy = new VorbisCommentDetectionStrategy();
    private final MP4DetectionStrategy mp4Strategy = new MP4DetectionStrategy();
    private final AIFFDetectionStrategy aiffStrategy = new AIFFDetectionStrategy();
    private final ASFDetectionStrategy asfStrategy = new ASFDetectionStrategy();
    private final FLACApplicationDetectionStrategy flacAppStrategy = new FLACApplicationDetectionStrategy();
    private final MatroskaDetectionStrategy matroskaStrategy = new MatroskaDetectionStrategy();
    private final DSDDetectionStrategy dsdStrategy = new DSDDetectionStrategy();
    private final TTADetectionStrategy ttaStrategy = new TTADetectionStrategy();
    private final WavPackDetectionStrategy wavPackStrategy = new WavPackDetectionStrategy();

    /**
     * Haupteingang: Nur noch filePath und config benötigt
     */
    public List<TagInfo> detectTags(String filePath, ScanConfiguration config) throws IOException {
        // File validation
        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            throw new IOException("Datei existiert nicht oder ist nicht lesbar: " + filePath);
        }

        // Extract file extension
        String extension = getFileExtension(filePath);

        // Determine formats to check based on configuration
        List<TagFormat> formatsToCheck = switch (config.getMode()) {
            case FULL_SCAN -> FormatPriorityManager.getFullScanPriority();
            case COMFORT_SCAN -> FormatPriorityManager.getComfortScanPriority(extension);
            case CUSTOM_SCAN -> config.getCustomFormats();
            default -> throw new IllegalArgumentException("Unknown Scan-Mode: " + config.getMode());
        };

        Log.debug("Start Tag-Detection with Mode: {} for File: {}", config.getMode(), filePath);

        // File I/O and detection
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            // Read buffers for start and end of file
            FileBuffers buffers = readFileBuffers(raf);

            // Perform tag detection
            List<TagInfo> detectedTags = performDetection(formatsToCheck, raf, filePath, extension, buffers);

            Log.info("Detected {} tags in {} using {}", detectedTags.size(), filePath, config.getMode());
            return detectedTags;

        } catch (IOException e) {
            Log.error("Fehler beim Lesen der Datei {}: {}", filePath, e.getMessage());
            throw e;
        }
    }

    /**
     * Liest Start- und End-Buffer aus der Datei
     */
    private FileBuffers readFileBuffers(RandomAccessFile raf) throws IOException {
        byte[] startBuffer = new byte[BUFFER_SIZE];
        byte[] endBuffer = new byte[BUFFER_SIZE];

        // Read start buffer
        raf.seek(0);
        int startRead = raf.read(startBuffer);

        // Read end buffer
        long endPosition = Math.max(0, raf.length() - BUFFER_SIZE);
        raf.seek(endPosition);
        int endRead = raf.read(endBuffer);

        // If file is smaller than buffer, adjust buffers
        if (raf.length() < BUFFER_SIZE) {
            byte[] actualStartBuffer = new byte[startRead];
            System.arraycopy(startBuffer, 0, actualStartBuffer, 0, startRead);
            startBuffer = actualStartBuffer;
            endBuffer = actualStartBuffer; // Same buffer for small files
        }

        return new FileBuffers(startBuffer, endBuffer);
    }

    /**
     * Führt die eigentliche Tag-Detection durch
     */
    private List<TagInfo> performDetection(List<TagFormat> formatsToCheck, RandomAccessFile raf,
                                           String filePath, String extension, FileBuffers buffers) throws IOException {
        List<TagInfo> detectedTags = new ArrayList<>();

        // Direct format-to-strategy mapping - only check relevant strategies
        for (TagFormat format : formatsToCheck) {
            try {
                List<TagInfo> tags = detectFormatDirect(format, raf, filePath, buffers);
                detectedTags.addAll(tags);
            } catch (Exception e) {
                Log.warn("Error detecting format {} in file {}: {}", format, filePath, e.getMessage());
            }
        }

        Log.debug("Recognized {} Tags", detectedTags.size());
        return detectedTags;
    }

    /**
     * Direct format detection - cleaner switch without repetitive yield blocks
     */
    private List<TagInfo> detectFormatDirect(TagFormat format, RandomAccessFile file, String filePath,
                                             FileBuffers buffers) throws IOException {

        return switch (format) {
            case ID3V2_3, ID3V2_4, ID3V2_2 ->
                    detectWithStrategy(id3v2Strategy, format, file, filePath, buffers);

            case ID3V1, ID3V1_1 ->
                    detectWithStrategy(id3v1Strategy, format, file, filePath, buffers);

            case VORBIS_COMMENT ->
                    detectWithStrategy(vorbisStrategy, null, file, filePath, buffers);

            case MP4 ->
                    detectWithStrategy(mp4Strategy, null, file, filePath, buffers);

            case APEV2, APEV1 ->
                    detectWithStrategy(apeStrategy, format, file, filePath, buffers);

            case ASF_CONTENT_DESC, ASF_EXT_CONTENT_DESC ->
                    detectWithStrategy(asfStrategy, format, file, filePath, buffers);

            case RIFF_INFO, BWF_V2, BWF_V1, BWF_V0 ->
                    detectWithStrategy(wavStrategy, format, file, filePath, buffers);

            case FLAC_APPLICATION ->
                    detectWithStrategy(flacAppStrategy, null, file, filePath, buffers);

            case MATROSKA_TAGS, WEBM_TAGS ->
                    detectWithStrategy(matroskaStrategy, format, file, filePath, buffers);

            case DSF_METADATA, DFF_METADATA ->
                    detectWithStrategy(dsdStrategy, format, file, filePath, buffers);

            case TTA_METADATA ->
                    detectWithStrategy(ttaStrategy, null, file, filePath, buffers);

            case WAVPACK_NATIVE ->
                    detectWithStrategy(wavPackStrategy, null, file, filePath, buffers);

            case AIFF_METADATA ->
                    detectWithStrategy(aiffStrategy, null, file, filePath, buffers);

            case LYRICS3V2, LYRICS3V1 ->
                    detectWithStrategy(lyrics3Strategy, format, file, filePath, buffers);

            default -> {
                Log.warn("Unknown format requested: {}", format);
                yield List.of();
            }
        };
    }

    /**
     * Unified helper method for all detection strategies
     * @param strategy The detection strategy to use
     * @param requestedFormat The specific format to filter for (null = return all)
     */
    private List<TagInfo> detectWithStrategy(TagDetectionStrategy strategy, TagFormat requestedFormat,
                                             RandomAccessFile file, String filePath,
                                             FileBuffers buffers) throws IOException {
        if (!strategy.canDetect(buffers.startBuffer, buffers.endBuffer)) {
            return List.of();
        }

        List<TagInfo> allTags = strategy.detectTags(file, filePath, buffers.startBuffer, buffers.endBuffer);

        // Filter only if specific format requested, otherwise return all
        if (requestedFormat != null) {
            return allTags.stream()
                    .filter(tag -> tag.getFormat() == requestedFormat)
                    .toList();
        }

        return allTags;
    }

    /**
     * Hilfsmethode: Dateiendung extrahieren
     */
    private String getFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        return dotIndex == -1 ? "" : filePath.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * Data class für File-Buffer
     */
    private record FileBuffers(byte[] startBuffer, byte[] endBuffer) {}
}