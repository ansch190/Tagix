package com.schwanitz.strategies.parsing.factory;

import com.schwanitz.strategies.parsing.*;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.util.Collections;
import java.util.Map;

/**
 * Factory, die {@link TagFormat}-Werte auf die zugehörigen {@link TagParsingStrategy}-Implementierungen abbildet.
 *
 * <p>Diese Factory zentralisiert die Zuordnung zwischen Tag-Formaten und deren Parser-Strategien.
 * Sie ist als instanzbasierte Klasse konzipiert und kann via Dependency Injection übergeben oder
 * für Unit-Tests mit einem benutzerdefinierten Strategie-Mapping instanziiert werden.</p>
 *
 * @see TagParsingStrategy
 * @see TagFormat
 */
public class TagParsingStrategyFactory {

    private final Map<TagFormat, TagParsingStrategy> formatToStrategy;

    /**
     * Erzeugt eine neue Factory mit allen Standard-Parser-Strategien.
     */
    public TagParsingStrategyFactory() {
        ID3ParsingStrategy id3Parser = new ID3ParsingStrategy();
        APEParsingStrategy apeParser = new APEParsingStrategy();
        VorbisParsingStrategy vorbisParser = new VorbisParsingStrategy();
        MP4ParsingStrategy mp4Parser = new MP4ParsingStrategy();
        RIFFInfoParsingStrategy riffParser = new RIFFInfoParsingStrategy();
        BWFParsingStrategy bwfParser = new BWFParsingStrategy();
        AIFFMetadataParsingStrategy aiffParser = new AIFFMetadataParsingStrategy();
        Lyrics3ParsingStrategy lyrics3Parser = new Lyrics3ParsingStrategy();
        ASFParsingStrategy asfParser = new ASFParsingStrategy();
        DSFParsingStrategy dsfParser = new DSFParsingStrategy();
        DFFParsingStrategy dffParser = new DFFParsingStrategy();
        MatroskaParsingStrategy matroskaParser = new MatroskaParsingStrategy();
        WavPackParsingStrategy wavPackParser = new WavPackParsingStrategy();
        FLACApplicationParsingStrategy flacAppParser = new FLACApplicationParsingStrategy();

        this.formatToStrategy = Map.ofEntries(
                Map.entry(TagFormat.ID3V1, id3Parser),
                Map.entry(TagFormat.ID3V1_1, id3Parser),
                Map.entry(TagFormat.ID3V2_2, id3Parser),
                Map.entry(TagFormat.ID3V2_3, id3Parser),
                Map.entry(TagFormat.ID3V2_4, id3Parser),
                Map.entry(TagFormat.APEV1, apeParser),
                Map.entry(TagFormat.APEV2, apeParser),
                Map.entry(TagFormat.VORBIS_COMMENT, vorbisParser),
                Map.entry(TagFormat.MP4, mp4Parser),
                Map.entry(TagFormat.RIFF_INFO, riffParser),
                Map.entry(TagFormat.BWF_V0, bwfParser),
                Map.entry(TagFormat.BWF_V1, bwfParser),
                Map.entry(TagFormat.BWF_V2, bwfParser),
                Map.entry(TagFormat.AIFF_METADATA, aiffParser),
                Map.entry(TagFormat.LYRICS3V1, lyrics3Parser),
                Map.entry(TagFormat.LYRICS3V2, lyrics3Parser),
                Map.entry(TagFormat.ASF_CONTENT_DESC, asfParser),
                Map.entry(TagFormat.ASF_EXT_CONTENT_DESC, asfParser),
                Map.entry(TagFormat.DSF_METADATA, dsfParser),
                Map.entry(TagFormat.DFF_METADATA, dffParser),
                Map.entry(TagFormat.MATROSKA_TAGS, matroskaParser),
                Map.entry(TagFormat.WEBM_TAGS, matroskaParser),
                Map.entry(TagFormat.WAVPACK_NATIVE, wavPackParser),
                Map.entry(TagFormat.FLAC_APPLICATION, flacAppParser)
        );
    }

    /**
     * Erzeugt eine neue Factory mit einem benutzerdefinierten Strategie-Mapping.
     * <p>Nützlich für Unit-Tests mit gemockten Strategien.</p>
     *
     * @param formatToStrategy das Format-zu-Strategie-Mapping
     */
    public TagParsingStrategyFactory(Map<TagFormat, TagParsingStrategy> formatToStrategy) {
        this.formatToStrategy = Collections.unmodifiableMap(formatToStrategy);
    }

    /**
     * Liefert die passende {@link TagParsingStrategy} für das angegebene {@link TagFormat}.
     *
     * @param format das gewünschte Tag-Format
     * @return die zugehörige Parsing-Strategie oder {@code null}, wenn kein Parser registriert ist
     */
    public TagParsingStrategy getStrategyForFormat(TagFormat format) {
        return formatToStrategy.get(format);
    }
}
