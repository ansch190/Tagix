package com.schwanitz.strategies.detection.context;

import com.schwanitz.strategies.detection.*;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.FormatPriorityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimized FormatDetectionContext using direct format-to-strategy mapping
 * <p>
 * Instead of iterating through all strategies and checking relevance,
 * this implementation directly calls only the strategies needed for
 * the requested formats.
 */
public class FormatDetectionContext {

    private static final Logger Log = LoggerFactory.getLogger(FormatDetectionContext.class);

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
     * Detect tags according to scan configuration
     */
    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, String fileExtension,
                                    byte[] startBuffer, byte[] endBuffer, ScanConfiguration config) throws IOException {

        Log.debug("Start Tag-Detection with Mode: {} for File: {}", config.getMode(), filePath);

        List<TagFormat> formatsToCheck = switch (config.getMode()) {
            case FULL_SCAN -> FormatPriorityManager.getFullScanPriority();
            case COMFORT_SCAN -> FormatPriorityManager.getComfortScanPriority(fileExtension);
            case CUSTOM_SCAN -> config.getCustomFormats();
            default -> throw new IllegalArgumentException("Unknown Scan-Mode: " + config.getMode());
        };

        List<TagInfo> detectedTags = new ArrayList<>();

        // Direct format-to-strategy mapping - only check relevant strategies
        for (TagFormat format : formatsToCheck) {
            try {
                List<TagInfo> tags = detectFormatDirect(format, file, filePath, startBuffer, endBuffer);
                detectedTags.addAll(tags);
            } catch (Exception e) {
                Log.warn("Error detecting format {} in file {}: {}", format, filePath, e.getMessage());
            }
        }

        Log.debug("Recognized {} Tags", detectedTags.size());
        return detectedTags;
    }

    /**
     * Direct format detection - maps format to appropriate strategy
     * Cases are ordered according to FormatPriorityManager.FULL_SCAN_PRIORITY
     */
    private List<TagInfo> detectFormatDirect(TagFormat format, RandomAccessFile file, String filePath,
                                             byte[] startBuffer, byte[] endBuffer) throws IOException {

        return switch (format) {
            // ID3v2 Formats (ID3V2DetectionStrategy handles 2.2, 2.3, 2.4)
            case ID3V2_3, ID3V2_4, ID3V2_2 -> {
                if (id3v2Strategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = id3v2Strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            // ID3v1 Formats (ID3V1DetectionStrategy handles v1 and v1.1)
            case ID3V1, ID3V1_1 -> {
                if (id3v1Strategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = id3v1Strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            // Vorbis Comments (single format)
            case VORBIS_COMMENT -> {
                if (vorbisStrategy.canDetect(startBuffer, endBuffer)) {
                    yield vorbisStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                }
                yield List.of();
            }

            // MP4/iTunes Tags (single format)
            case MP4 -> {
                if (mp4Strategy.canDetect(startBuffer, endBuffer)) {
                    yield mp4Strategy.detectTags(file, filePath, startBuffer, endBuffer);
                }
                yield List.of();
            }

            // APE Tags (APEDetectionStrategy handles v1 and v2)
            case APEV2, APEV1 -> {
                if (apeStrategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = apeStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            // ASF/WMA Tags (ASFDetectionStrategy handles both content types)
            case ASF_CONTENT_DESC, ASF_EXT_CONTENT_DESC -> {
                if (asfStrategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = asfStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            // RIFF/WAV Formats (WAVDetectionStrategy handles all BWF versions + RIFF_INFO)
            case RIFF_INFO, BWF_V2, BWF_V1, BWF_V0 -> {
                if (wavStrategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = wavStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            // FLAC Application Blocks (single format)
            case FLAC_APPLICATION -> {
                if (flacAppStrategy.canDetect(startBuffer, endBuffer)) {
                    yield flacAppStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                }
                yield List.of();
            }

            // Container-specific Formats (MatroskaDetectionStrategy handles both)
            case MATROSKA_TAGS, WEBM_TAGS -> {
                if (matroskaStrategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = matroskaStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            // High-End Audio DSD Formats (DSDDetectionStrategy handles both)
            case DSF_METADATA, DFF_METADATA -> {
                if (dsdStrategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = dsdStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            // TrueAudio (single format)
            case TTA_METADATA -> {
                if (ttaStrategy.canDetect(startBuffer, endBuffer)) {
                    yield ttaStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                }
                yield List.of();
            }

            // WavPack (single format)
            case WAVPACK_NATIVE -> {
                if (wavPackStrategy.canDetect(startBuffer, endBuffer)) {
                    yield wavPackStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                }
                yield List.of();
            }

            // AIFF (single format)
            case AIFF_METADATA -> {
                if (aiffStrategy.canDetect(startBuffer, endBuffer)) {
                    yield aiffStrategy.detectTags(file, filePath, startBuffer, endBuffer);
                }
                yield List.of();
            }

            // Lyrics3 Formats (Lyrics3DetectionStrategy handles v1 and v2)
            case LYRICS3V2, LYRICS3V1 -> {
                if (lyrics3Strategy.canDetect(startBuffer, endBuffer)) {
                    List<TagInfo> tags = lyrics3Strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    yield tags.stream().filter(tag -> tag.getFormat() == format).toList();
                }
                yield List.of();
            }

            default -> {
                Log.warn("Unknown format requested: {}", format);
                yield List.of();
            }
        };
    }
}