package com.schwanitz.strategies.writing.factory;

import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.*;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.util.Collections;
import java.util.Map;

/**
 * Factory, die {@link TagFormat}-Werte auf die zugehörigen {@link TagWritingStrategy}-Implementierungen abbildet.
 * <p>
 * Diese Factory zentralisiert die Zuordnung zwischen Tag-Formaten und deren Schreib-Strategien.
 * Sie ist als instanzbasierte Klasse konzipiert und kann via Dependency Injection übergeben oder
 * für Unit-Tests mit einem benutzerdefinierten Strategie-Mapping instanziiert werden.
 * </p>
 *
 * @see TagWritingStrategy
 * @see TagFormat
 */
public class TagWritingStrategyFactory {

    private final Map<TagFormat, TagWritingStrategy> formatToStrategy;

    /**
     * Erzeugt eine neue Factory mit allen Standard-Schreib-Strategien.
     */
    public TagWritingStrategyFactory() {
        this(new TagParsingStrategyFactory());
    }

    /**
     * Erzeugt eine neue Factory mit den angegebenen Parsing-Strategien für die Schreib-Strategien.
     *
     * @param parsingFactory die Factory zum Lesen bestehender Tags
     */
    public TagWritingStrategyFactory(TagParsingStrategyFactory parsingFactory) {
        ID3WritingStrategy id3Writer = new ID3WritingStrategy(parsingFactory);
        VorbisWritingStrategy vorbisWriter = new VorbisWritingStrategy(parsingFactory);
        APEWritingStrategy apeWriter = new APEWritingStrategy(parsingFactory);
        MP4WritingStrategy mp4Writer = new MP4WritingStrategy(parsingFactory);
        RIFFWritingStrategy riffWriter = new RIFFWritingStrategy(parsingFactory);
        ASFWritingStrategy asfWriter = new ASFWritingStrategy(parsingFactory);
        AIFFWritingStrategy aiffWriter = new AIFFWritingStrategy(parsingFactory);
        MatroskaWritingStrategy matroskaWriter = new MatroskaWritingStrategy(parsingFactory);
        DSFWritingStrategy dsfWriter = new DSFWritingStrategy(parsingFactory);
        DFFWritingStrategy dffWriter = new DFFWritingStrategy(parsingFactory);
        WavPackWritingStrategy wavPackWriter = new WavPackWritingStrategy(parsingFactory);
        Lyrics3WritingStrategy lyrics3Writer = new Lyrics3WritingStrategy(parsingFactory);

        this.formatToStrategy = Map.ofEntries(
                Map.entry(TagFormat.ID3V1, id3Writer),
                Map.entry(TagFormat.ID3V1_1, id3Writer),
                Map.entry(TagFormat.ID3V2_2, id3Writer),
                Map.entry(TagFormat.ID3V2_3, id3Writer),
                Map.entry(TagFormat.ID3V2_4, id3Writer),
                Map.entry(TagFormat.APEV1, apeWriter),
                Map.entry(TagFormat.APEV2, apeWriter),
                Map.entry(TagFormat.VORBIS_COMMENT, vorbisWriter),
                Map.entry(TagFormat.MP4, mp4Writer),
                Map.entry(TagFormat.RIFF_INFO, riffWriter),
                Map.entry(TagFormat.BWF_V0, riffWriter),
                Map.entry(TagFormat.BWF_V1, riffWriter),
                Map.entry(TagFormat.BWF_V2, riffWriter),
                Map.entry(TagFormat.AIFF_METADATA, aiffWriter),
                Map.entry(TagFormat.LYRICS3V1, lyrics3Writer),
                Map.entry(TagFormat.LYRICS3V2, lyrics3Writer),
                Map.entry(TagFormat.ASF_CONTENT_DESC, asfWriter),
                Map.entry(TagFormat.ASF_EXT_CONTENT_DESC, asfWriter),
                Map.entry(TagFormat.DSF_METADATA, dsfWriter),
                Map.entry(TagFormat.DFF_METADATA, dffWriter),
                Map.entry(TagFormat.MATROSKA_TAGS, matroskaWriter),
                Map.entry(TagFormat.WEBM_TAGS, matroskaWriter),
                Map.entry(TagFormat.WAVPACK_NATIVE, wavPackWriter),
                Map.entry(TagFormat.FLAC_APPLICATION, vorbisWriter)
        );
    }

    /**
     * Erzeugt eine neue Factory mit einem benutzerdefinierten Strategie-Mapping.
     * <p>
     * Nützlich für Unit-Tests mit gemockten Strategien.
     * </p>
     *
     * @param formatToStrategy das Format-zu-Strategie-Mapping
     */
    public TagWritingStrategyFactory(Map<TagFormat, TagWritingStrategy> formatToStrategy) {
        this.formatToStrategy = Collections.unmodifiableMap(formatToStrategy);
    }

    /**
     * Liefert die passende {@link TagWritingStrategy} für das angegebene {@link TagFormat}.
     *
     * @param format das gewünschte Tag-Format
     * @return die zugehörige Schreib-Strategie oder {@code null}, wenn kein Parser registriert ist
     */
    public TagWritingStrategy getStrategyForFormat(TagFormat format) {
        return formatToStrategy.get(format);
    }
}
