package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class ID3FrameParserRegistryTest {

    private final ID3FrameParserRegistry registry = new ID3FrameParserRegistry();

    @Nested
    class ExactMatchFrames {

        @Test
        void getParser_TXXX_returnsUserDefinedTextFrameParser() {
            assertThat(registry.getParser("TXXX")).isInstanceOf(UserDefinedTextFrameParser.class);
        }

        @Test
        void getParser_COMM_returnsCommentFrameParser() {
            assertThat(registry.getParser("COMM")).isInstanceOf(CommentFrameParser.class);
        }

        @Test
        void getParser_USLT_returnsLyricsFrameParser() {
            assertThat(registry.getParser("USLT")).isInstanceOf(LyricsFrameParser.class);
        }

        @Test
        void getParser_APIC_returnsPictureFrameParser() {
            assertThat(registry.getParser("APIC")).isInstanceOf(PictureFrameParser.class);
        }

        @Test
        void getParser_PCNT_returnsPlayCounterFrameParser() {
            assertThat(registry.getParser("PCNT")).isInstanceOf(PlayCounterFrameParser.class);
        }

        @Test
        void getParser_POPM_returnsPopularimeterFrameParser() {
            assertThat(registry.getParser("POPM")).isInstanceOf(PopularimeterFrameParser.class);
        }

        @Test
        void getParser_GEOB_returnsGeneralObjectFrameParser() {
            assertThat(registry.getParser("GEOB")).isInstanceOf(GeneralObjectFrameParser.class);
        }

        @Test
        void getParser_PRIV_returnsPrivateFrameParser() {
            assertThat(registry.getParser("PRIV")).isInstanceOf(PrivateFrameParser.class);
        }
    }

    @Nested
    class PrefixMatchFrames {

        @Test
        void getParser_TPrefix_returnsTextFrameParser() {
            assertThat(registry.getParser("TIT2")).isInstanceOf(TextFrameParser.class);
            assertThat(registry.getParser("TALB")).isInstanceOf(TextFrameParser.class);
            assertThat(registry.getParser("TPE1")).isInstanceOf(TextFrameParser.class);
        }

        @Test
        void getParser_WPrefix_returnsUrlFrameParser() {
            assertThat(registry.getParser("WCOM")).isInstanceOf(UrlFrameParser.class);
            assertThat(registry.getParser("WCOP")).isInstanceOf(UrlFrameParser.class);
        }

        @Test
        void getParser_TXXX_exactOverridesPrefix() {
            assertThat(registry.getParser("TXXX")).isInstanceOf(UserDefinedTextFrameParser.class);
            assertThat(registry.getParser("TXXX")).isNotInstanceOf(TextFrameParser.class);
        }

        @Test
        void getParser_WXXX_exactOverridesPrefix() {
            assertThat(registry.getParser("WXXX")).isInstanceOf(UrlFrameParser.class);
        }
    }

    @Nested
    class UnknownFrameId {

        @Test
        void getParser_unknownId_returnsNull() {
            assertThat(registry.getParser("XXXX")).isNull();
            assertThat(registry.getParser("ABCD")).isNull();
        }

        @Test
        void getParser_emptyId_returnsNull() {
            assertThat(registry.getParser("")).isNull();
        }
    }

    @Nested
    class RegisterExactOverridesPrefix {

        @Test
        void registerExact_overridesPrefixMatch() {
            ID3FrameParserRegistry reg = new ID3FrameParserRegistry();
            ID3FrameParser customParser = (data, frameId, majorVersion) -> "custom";
            reg.registerExact("TIT2", customParser);

            assertThat(reg.getParser("TIT2")).isSameAs(customParser);
        }
    }

    @Nested
    class V22Aliases {

        @Test
        void getParser_TXX_returnsUserDefinedTextFrameParser() {
            assertThat(registry.getParser("TXX")).isInstanceOf(UserDefinedTextFrameParser.class);
        }

        @Test
        void getParser_COM_returnsCommentFrameParser() {
            assertThat(registry.getParser("COM")).isInstanceOf(CommentFrameParser.class);
        }

        @Test
        void getParser_ULT_returnsLyricsFrameParser() {
            assertThat(registry.getParser("ULT")).isInstanceOf(LyricsFrameParser.class);
        }

        @Test
        void getParser_PIC_returnsPictureFrameParser() {
            assertThat(registry.getParser("PIC")).isInstanceOf(PictureFrameParser.class);
        }

        @Test
        void getParser_CNT_returnsPlayCounterFrameParser() {
            assertThat(registry.getParser("CNT")).isInstanceOf(PlayCounterFrameParser.class);
        }

        @Test
        void getParser_POP_returnsPopularimeterFrameParser() {
            assertThat(registry.getParser("POP")).isInstanceOf(PopularimeterFrameParser.class);
        }

        @Test
        void getParser_GEO_returnsGeneralObjectFrameParser() {
            assertThat(registry.getParser("GEO")).isInstanceOf(GeneralObjectFrameParser.class);
        }
    }
}