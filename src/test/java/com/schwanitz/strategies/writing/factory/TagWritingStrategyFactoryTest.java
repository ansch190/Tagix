package com.schwanitz.strategies.writing.factory;

import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.WriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TagWritingStrategyFactory Tests")
class TagWritingStrategyFactoryTest {

    private TagWritingStrategyFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TagWritingStrategyFactory();
    }

    @Test
    @DisplayName("ID3V1 wird von ID3-Strategie behandelt")
    void id3v1() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.ID3V1);
        assertNotNull(strategy);
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.ID3V1));
    }

    @Test
    @DisplayName("ID3V2_4 wird von ID3-Strategie behandelt")
    void id3v24() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.ID3V2_4);
        assertNotNull(strategy);
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.ID3V2_4));
    }

    @Test
    @DisplayName("APEV2 wird von APE-Strategie behandelt")
    void apev2() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.APEV2);
        assertNotNull(strategy);
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.APEV2));
    }

    @Test
    @DisplayName("VORBIS_COMMENT wird von Vorbis-Strategie behandelt")
    void vorbisComment() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.VORBIS_COMMENT);
        assertNotNull(strategy);
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.VORBIS_COMMENT));
    }

    @Test
    @DisplayName("MP4 wird von MP4-Strategie behandelt")
    void mp4() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.MP4);
        assertNotNull(strategy);
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.MP4));
    }

    @Test
    @DisplayName("RIFF_INFO wird von RIFF-Strategie behandelt")
    void riffInfo() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.RIFF_INFO);
        assertNotNull(strategy);
        assertTrue(strategy.getSupportedWriteFormats().contains(TagFormat.RIFF_INFO));
    }

    @Test
    @DisplayName("BWF_V1 wird von RIFF-Strategie behandelt")
    void bwfV1() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.BWF_V1);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("ASF_CONTENT_DESC wird von ASF-Strategie behandelt")
    void asfContentDesc() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.ASF_CONTENT_DESC);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("AIFF_METADATA wird von AIFF-Strategie behandelt")
    void aiffMetadata() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.AIFF_METADATA);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("MATROSKA_TAGS wird von Matroska-Strategie behandelt")
    void matroskaTags() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.MATROSKA_TAGS);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("DSF_METADATA wird von DSF-Strategie behandelt")
    void dsfMetadata() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.DSF_METADATA);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("DFF_METADATA wird von DFF-Strategie behandelt")
    void dffMetadata() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.DFF_METADATA);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("WAVPACK_NATIVE wird von WavPack-Strategie behandelt")
    void wavpackNative() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.WAVPACK_NATIVE);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("LYRICS3V2 wird von Lyrics3-Strategie behandelt")
    void lyrics3v2() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.LYRICS3V2);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("FLAC_APPLICATION wird von Vorbis-Strategie behandelt")
    void flacApplication() {
        TagWritingStrategy strategy = factory.getStrategyForFormat(TagFormat.FLAC_APPLICATION);
        assertNotNull(strategy);
    }

    @Test
    @DisplayName("Custom Mapping mit Map-Constructor")
    void customMapping() {
        Map<TagFormat, TagWritingStrategy> map = Map.of(
                TagFormat.ID3V2_4, new DummyStrategy(),
                TagFormat.APEV2, new DummyStrategy()
        );
        TagWritingStrategyFactory custom = new TagWritingStrategyFactory(map);
        assertNotNull(custom.getStrategyForFormat(TagFormat.ID3V2_4));
        assertNotNull(custom.getStrategyForFormat(TagFormat.APEV2));
        assertNull(custom.getStrategyForFormat(TagFormat.MP4));
    }

    @Test
    @DisplayName("Alle ID3-Formate werden behandelt")
    void allId3Formats() {
        assertNotNull(factory.getStrategyForFormat(TagFormat.ID3V1));
        assertNotNull(factory.getStrategyForFormat(TagFormat.ID3V1_1));
        assertNotNull(factory.getStrategyForFormat(TagFormat.ID3V2_2));
        assertNotNull(factory.getStrategyForFormat(TagFormat.ID3V2_3));
        assertNotNull(factory.getStrategyForFormat(TagFormat.ID3V2_4));
    }

    @Test
    @DisplayName("Alle APE-Formate werden behandelt")
    void allApeFormats() {
        assertNotNull(factory.getStrategyForFormat(TagFormat.APEV1));
        assertNotNull(factory.getStrategyForFormat(TagFormat.APEV2));
    }

    @Test
    @DisplayName("Alle ASF-Formate werden behandelt")
    void allAsfFormats() {
        assertNotNull(factory.getStrategyForFormat(TagFormat.ASF_CONTENT_DESC));
        assertNotNull(factory.getStrategyForFormat(TagFormat.ASF_EXT_CONTENT_DESC));
    }

    @Test
    @DisplayName("Alle Matroska-Formate werden behandelt")
    void allMatroskaFormats() {
        assertNotNull(factory.getStrategyForFormat(TagFormat.MATROSKA_TAGS));
        assertNotNull(factory.getStrategyForFormat(TagFormat.WEBM_TAGS));
    }

    /**
     * Dummy-Strategie für Custom-Mapping-Test.
     */
    static class DummyStrategy implements TagWritingStrategy {
        @Override
        public WriteResult writeTag(com.schwanitz.tagging.TagFormat format,
                                     com.schwanitz.interfaces.Metadata metadata,
                                     com.schwanitz.io.SeekableDataSource source,
                                     com.schwanitz.tagging.TagInfo existingTag,
                                     com.schwanitz.tagging.WriteConfiguration config) {
            return WriteResult.success(format, 0, 0);
        }

        @Override
        public java.util.List<com.schwanitz.tagging.TagFormat> getSupportedWriteFormats() {
            return java.util.List.of();
        }

        @Override
        public boolean supportsInPlaceWrite(com.schwanitz.tagging.TagFormat format) {
            return false;
        }
    }
}
