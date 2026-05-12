package com.schwanitz.strategies.parsing.factory;

import com.schwanitz.strategies.parsing.*;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.util.Map;

/**
 * Factory die TagFormate auf ihre Parsing-Strategies mapped.
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
            Map.entry(TagFormat.LYRICS3V2, LYRICS3_PARSER)
    );

    public static TagParsingStrategy getStrategyForFormat(TagFormat format) {
        return FORMAT_TO_STRATEGY.get(format);
    }
}
