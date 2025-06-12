package com.schwanitz.strategies.detection.factory;

import com.schwanitz.strategies.detection.*;
import com.schwanitz.strategies.detection.context.TagDetectionStrategy;
import com.schwanitz.tagging.TagFormat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Einfache Strategy Factory: TagFormat(s) -> Strategy List
 */
public class TagDetectionStrategyFactory {

    // Singleton Strategy-Instanzen (thread-safe, da immutable)
    private static final ID3V1DetectionStrategy ID3V1_STRATEGY = new ID3V1DetectionStrategy();
    private static final ID3V2DetectionStrategy ID3V2_STRATEGY = new ID3V2DetectionStrategy();
    private static final APEDetectionStrategy APE_STRATEGY = new APEDetectionStrategy();
    private static final VorbisCommentDetectionStrategy VORBIS_STRATEGY = new VorbisCommentDetectionStrategy();
    private static final MP4DetectionStrategy MP4_STRATEGY = new MP4DetectionStrategy();
    private static final WAVDetectionStrategy WAV_STRATEGY = new WAVDetectionStrategy();
    private static final ASFDetectionStrategy ASF_STRATEGY = new ASFDetectionStrategy();
    private static final FLACApplicationDetectionStrategy FLAC_APP_STRATEGY = new FLACApplicationDetectionStrategy();
    private static final MatroskaDetectionStrategy MATROSKA_STRATEGY = new MatroskaDetectionStrategy();
    private static final DSDDetectionStrategy DSD_STRATEGY = new DSDDetectionStrategy();
    private static final TTADetectionStrategy TTA_STRATEGY = new TTADetectionStrategy();
    private static final WavPackDetectionStrategy WAVPACK_STRATEGY = new WavPackDetectionStrategy();
    private static final AIFFDetectionStrategy AIFF_STRATEGY = new AIFFDetectionStrategy();
    private static final Lyrics3DetectionStrategy LYRICS3_STRATEGY = new Lyrics3DetectionStrategy();

    // Mapping: TagFormat -> Strategy
    private static final Map<TagFormat, TagDetectionStrategy> FORMAT_TO_STRATEGY = Map.ofEntries(
            // ID3 Formate
            Map.entry(TagFormat.ID3V1, ID3V1_STRATEGY),
            Map.entry(TagFormat.ID3V1_1, ID3V1_STRATEGY),
            Map.entry(TagFormat.ID3V2_2, ID3V2_STRATEGY),
            Map.entry(TagFormat.ID3V2_3, ID3V2_STRATEGY),
            Map.entry(TagFormat.ID3V2_4, ID3V2_STRATEGY),

            // APE Formate
            Map.entry(TagFormat.APEV1, APE_STRATEGY),
            Map.entry(TagFormat.APEV2, APE_STRATEGY),

            // Andere Formate
            Map.entry(TagFormat.VORBIS_COMMENT, VORBIS_STRATEGY),
            Map.entry(TagFormat.MP4, MP4_STRATEGY),

            // WAV Formate
            Map.entry(TagFormat.RIFF_INFO, WAV_STRATEGY),
            Map.entry(TagFormat.BWF_V0, WAV_STRATEGY),
            Map.entry(TagFormat.BWF_V1, WAV_STRATEGY),
            Map.entry(TagFormat.BWF_V2, WAV_STRATEGY),

            // ASF Formate
            Map.entry(TagFormat.ASF_CONTENT_DESC, ASF_STRATEGY),
            Map.entry(TagFormat.ASF_EXT_CONTENT_DESC, ASF_STRATEGY),

            // Weitere Formate
            Map.entry(TagFormat.FLAC_APPLICATION, FLAC_APP_STRATEGY),
            Map.entry(TagFormat.MATROSKA_TAGS, MATROSKA_STRATEGY),
            Map.entry(TagFormat.WEBM_TAGS, MATROSKA_STRATEGY),
            Map.entry(TagFormat.DSF_METADATA, DSD_STRATEGY),
            Map.entry(TagFormat.DFF_METADATA, DSD_STRATEGY),
            Map.entry(TagFormat.TTA_METADATA, TTA_STRATEGY),
            Map.entry(TagFormat.WAVPACK_NATIVE, WAVPACK_STRATEGY),
            Map.entry(TagFormat.AIFF_METADATA, AIFF_STRATEGY),
            Map.entry(TagFormat.LYRICS3V1, LYRICS3_STRATEGY),
            Map.entry(TagFormat.LYRICS3V2, LYRICS3_STRATEGY)
    );

    /**
     * Konvertiert eine Liste von TagFormats zu einer Liste von eindeutigen Strategies
     *
     * @param formats Liste der gewünschten TagFormate
     * @return Liste von Strategies (automatisch dedupliziert)
     */
    public static List<TagDetectionStrategy> getStrategiesForFormats(List<TagFormat> formats) {
        return formats.stream()
                .map(FORMAT_TO_STRATEGY::get)
                .filter(Objects::nonNull)
                .distinct()  // Automatische Deduplizierung
                .collect(Collectors.toList());
    }
}