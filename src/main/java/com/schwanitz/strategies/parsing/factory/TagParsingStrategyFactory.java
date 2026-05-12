package com.schwanitz.strategies.parsing.factory;

import com.schwanitz.strategies.parsing.*;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.util.Map;

/**
 * Factory, die {@link TagFormat}-Werte auf die zugehörigen {@link TagParsingStrategy}-Implementierungen abbildet.
 *
 * <p>Diese Factory zentralisiert die Zuordnung zwischen Tag-Formaten und deren Parser-Strategien.
 * Jedes unterstützte Format wird einem Instanz-Feld zugeordnet, sodass Mehrfachformate
 * (z.&nbsp;B. ID3V1 und ID3V2) denselben Parser teilen können. Die Zuordnung erfolgt über eine
 * unveränderliche {@link Map}, die bei Klassenladung initialisiert wird.</p>
 *
 * <p>Die Factory ist als Singleton-Konstrukt ohne Instanziierung konzipiert; ausschließlich die
 * statische Methode {@link #getStrategyForFormat(TagFormat)} wird verwendet.</p>
 *
 * @see TagParsingStrategy
 * @see TagFormat
 */
public class TagParsingStrategyFactory {

    private static final ID3ParsingStrategy ID3_PARSER = new ID3ParsingStrategy();
    private static final APEParsingStrategy APE_PARSER = new APEParsingStrategy();
    private static final VorbisParsingStrategy VORBIS_PARSER = new VorbisParsingStrategy();
    private static final MP4ParsingStrategy MP4_PARSER = new MP4ParsingStrategy();
    private static final RIFFInfoParsingStrategy RIFF_PARSER = new RIFFInfoParsingStrategy();
    private static final BWFParsingStrategy BWF_PARSER = new BWFParsingStrategy();
    private static final AIFFMetadataParsingStrategy AIFF_PARSER = new AIFFMetadataParsingStrategy();
    private static final Lyrics3ParsingStrategy LYRICS3_PARSER = new Lyrics3ParsingStrategy();
    private static final ASFParsingStrategy ASF_PARSER = new ASFParsingStrategy();
    private static final DSFParsingStrategy DSF_PARSER = new DSFParsingStrategy();
    private static final DFFParsingStrategy DFF_PARSER = new DFFParsingStrategy();
    private static final MatroskaParsingStrategy MATROSKA_PARSER = new MatroskaParsingStrategy();
    private static final WavPackParsingStrategy WAVPACK_PARSER = new WavPackParsingStrategy();
    private static final FLACApplicationParsingStrategy FLAC_APP_PARSER = new FLACApplicationParsingStrategy();

    private static final Map<TagFormat, TagParsingStrategy> FORMAT_TO_STRATEGY = Map.ofEntries(
            Map.entry(TagFormat.ID3V1, ID3_PARSER),
            Map.entry(TagFormat.ID3V1_1, ID3_PARSER),
            Map.entry(TagFormat.ID3V2_2, ID3_PARSER),
            Map.entry(TagFormat.ID3V2_3, ID3_PARSER),
            Map.entry(TagFormat.ID3V2_4, ID3_PARSER),
            Map.entry(TagFormat.APEV1, APE_PARSER),
            Map.entry(TagFormat.APEV2, APE_PARSER),
            Map.entry(TagFormat.VORBIS_COMMENT, VORBIS_PARSER),
            Map.entry(TagFormat.MP4, MP4_PARSER),
            Map.entry(TagFormat.RIFF_INFO, RIFF_PARSER),
            Map.entry(TagFormat.BWF_V0, BWF_PARSER),
            Map.entry(TagFormat.BWF_V1, BWF_PARSER),
            Map.entry(TagFormat.BWF_V2, BWF_PARSER),
            Map.entry(TagFormat.AIFF_METADATA, AIFF_PARSER),
            Map.entry(TagFormat.LYRICS3V1, LYRICS3_PARSER),
            Map.entry(TagFormat.LYRICS3V2, LYRICS3_PARSER),
            Map.entry(TagFormat.ASF_CONTENT_DESC, ASF_PARSER),
            Map.entry(TagFormat.ASF_EXT_CONTENT_DESC, ASF_PARSER),
            Map.entry(TagFormat.DSF_METADATA, DSF_PARSER),
            Map.entry(TagFormat.DFF_METADATA, DFF_PARSER),
            Map.entry(TagFormat.MATROSKA_TAGS, MATROSKA_PARSER),
            Map.entry(TagFormat.WEBM_TAGS, MATROSKA_PARSER),
            Map.entry(TagFormat.WAVPACK_NATIVE, WAVPACK_PARSER),
            Map.entry(TagFormat.FLAC_APPLICATION, FLAC_APP_PARSER)
    );

    /**
     * Liefert die passende {@link TagParsingStrategy} für das angegebene {@link TagFormat}.
     *
     * @param format das gewünschte Tag-Format
     * @return die zugehörige Parsing-Strategie oder {@code null}, wenn kein Parser registriert ist
     */
    public static TagParsingStrategy getStrategyForFormat(TagFormat format) {
        return FORMAT_TO_STRATEGY.get(format);
    }
}