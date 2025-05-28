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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FormatDetectionContext {

    private static final Logger Log = LoggerFactory.getLogger(FormatDetectionContext.class);

    private final Map<TagFormat, TagDetectionStrategy> strategies = new HashMap<>();

    public FormatDetectionContext() {
        initializeStrategies();
    }

    private void initializeStrategies() {
        strategies.put(TagFormat.ID3V1, new ID3V1DetectionStrategy());
        strategies.put(TagFormat.ID3V1_1, new ID3V1_1DetectionStrategy());
        strategies.put(TagFormat.ID3V2_2, new ID3V2_2DetectionStrategy());
        strategies.put(TagFormat.ID3V2_3, new ID3V2_3DetectionStrategy());
        strategies.put(TagFormat.ID3V2_4, new ID3V2_4DetectionStrategy());
        strategies.put(TagFormat.APEV1, new APEV1DetectionStrategy());
        strategies.put(TagFormat.APEV2, new APEV2DetectionStrategy());
        strategies.put(TagFormat.VORBIS_COMMENT, new VorbisCommentDetectionStrategy());
        strategies.put(TagFormat.MP4, new MP4DetectionStrategy());
        strategies.put(TagFormat.RIFF_INFO, new RIFFInfoDetectionStrategy());
        strategies.put(TagFormat.BWF_V0, new BWFDetectionStrategy());
        strategies.put(TagFormat.BWF_V1, new BWFDetectionStrategy());
        strategies.put(TagFormat.BWF_V2, new BWFDetectionStrategy());
        strategies.put(TagFormat.AIFF_METADATA, new AIFFDetectionStrategy());
        strategies.put(TagFormat.LYRICS3V1, new Lyrics3V1DetectionStrategy());
        strategies.put(TagFormat.LYRICS3V2, new Lyrics3V2DetectionStrategy());
    }

    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, String fileExtension,
                                    byte[] startBuffer, byte[] endBuffer, ScanConfiguration config) throws IOException {

        Log.debug("Starte Tag-Erkennung mit Modus: {} für Datei: {}", config.getMode(), filePath);

        List<TagFormat> formatsToCheck;
        switch (config.getMode()) {
            case FULL_SCAN:
                formatsToCheck = FormatPriorityManager.getFullScanPriority();
                break;
            case COMFORT_SCAN:
                formatsToCheck = FormatPriorityManager.getComfortScanPriority(fileExtension);
                break;
            case CUSTOM_SCAN:
                formatsToCheck = config.getCustomFormats();
                break;
            default:
                throw new IllegalArgumentException("Unbekannter Scan-Modus: " + config.getMode());
        }

        List<TagInfo> detectedTags = new ArrayList<>();

        for (TagFormat format : formatsToCheck) {
            TagDetectionStrategy strategy = strategies.get(format);
            if (strategy != null && strategy.canDetect(startBuffer, endBuffer)) {
                try {
                    List<TagInfo> tags = strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    detectedTags.addAll(tags);
                } catch (Exception e) {
                    Log.warn("Fehler bei der Erkennung von Format {} in Datei {}: {}", format, filePath, e.getMessage());
                }
            }
        }

        Log.debug("Erkannte {} Tags", detectedTags.size());
        return detectedTags;
    }
}