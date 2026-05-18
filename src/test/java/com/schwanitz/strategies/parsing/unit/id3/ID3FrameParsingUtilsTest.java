package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.ID3FrameParsingUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class ID3FrameParsingUtilsTest {

    @Nested
    class DecodeText {

        @Test
        void iso8859_v3() {
            byte[] data = "Hello".getBytes(StandardCharsets.ISO_8859_1);
            assertThat(ID3FrameParsingUtils.decodeText(data, 0, 3)).isEqualTo("Hello");
        }

        @Test
        void utf16_v3() {
            byte[] data = "Hello".getBytes(StandardCharsets.UTF_16);
            assertThat(ID3FrameParsingUtils.decodeText(data, 1, 3)).isEqualTo("Hello");
        }

        @Test
        void utf16BE_v3() {
            byte[] data = "Hello".getBytes(StandardCharsets.UTF_16BE);
            assertThat(ID3FrameParsingUtils.decodeText(data, 2, 3)).isEqualTo("Hello");
        }

        @Test
        void utf8_v4() {
            byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
            assertThat(ID3FrameParsingUtils.decodeText(data, 3, 4)).isEqualTo("Hello");
        }

        @Test
        void utf8_v3_fallsBackToIso8859() {
            byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
            assertThat(ID3FrameParsingUtils.decodeText(data, 3, 3)).isEqualTo("Hello");
        }

        @Test
        void unknownEncoding_fallsBackToIso8859() {
            byte[] data = "Hello".getBytes(StandardCharsets.ISO_8859_1);
            assertThat(ID3FrameParsingUtils.decodeText(data, 99, 3)).isEqualTo("Hello");
        }

        @Test
        void emptyData_returnsEmpty() {
            assertThat(ID3FrameParsingUtils.decodeText(new byte[0], 0, 3)).isEmpty();
        }

        @Test
        void embeddedNullCharacter_truncates() {
            byte[] data = {'H', 'i', 0, 'B', 'y', 'e'};
            assertThat(ID3FrameParsingUtils.decodeText(data, 0, 3)).isEqualTo("Hi");
        }

        @Test
        void trimsWhitespace() {
            byte[] data = "  Hello  ".getBytes(StandardCharsets.ISO_8859_1);
            assertThat(ID3FrameParsingUtils.decodeText(data, 0, 3)).isEqualTo("Hello");
        }
    }

    @Nested
    class ExtractFixedString {

        @Test
        void normalExtraction() {
            byte[] data = "Hello".getBytes(StandardCharsets.ISO_8859_1);
            assertThat(ID3FrameParsingUtils.extractFixedString(data, 0, 5)).isEqualTo("Hello");
        }

        @Test
        void nullByteTermination() {
            byte[] data = {'H', 'e', 0, 'l', 'o'};
            assertThat(ID3FrameParsingUtils.extractFixedString(data, 0, 5)).isEqualTo("He");
        }

        @Test
        void offsetBeyondData_returnsEmpty() {
            byte[] data = {'H', 'e'};
            assertThat(ID3FrameParsingUtils.extractFixedString(data, 5, 1)).isEmpty();
        }

        @Test
        void firstByteIsNull_returnsEmpty() {
            byte[] data = {0, 'H', 'i'};
            assertThat(ID3FrameParsingUtils.extractFixedString(data, 0, 3)).isEmpty();
        }

        @Test
        void partialExtraction() {
            byte[] data = "HelloWorld".getBytes(StandardCharsets.ISO_8859_1);
            assertThat(ID3FrameParsingUtils.extractFixedString(data, 0, 5)).isEqualTo("Hello");
        }
    }

    @Nested
    class FindNullTerminator {

        @Test
        void iso8859_findsSingleNull() {
            byte[] data = {'H', 'i', 0, 'B', 'y', 'e'};
            assertThat(ID3FrameParsingUtils.findNullTerminator(data, 0, 0)).isEqualTo(2);
        }

        @Test
        void utf16_findsDoubleNull() {
            byte[] data = {0x00, 0x48, 0x00, 0x00};
            assertThat(ID3FrameParsingUtils.findNullTerminator(data, 0, 1)).isEqualTo(2);
        }

        @Test
        void utf16BE_findsDoubleNull() {
            byte[] data = {0x00, 0x48, 0x00, 0x00};
            assertThat(ID3FrameParsingUtils.findNullTerminator(data, 0, 2)).isEqualTo(2);
        }

        @Test
        void noNullTerminator_returnsMinusOne() {
            byte[] data = {'H', 'e', 'l', 'l', 'o'};
            assertThat(ID3FrameParsingUtils.findNullTerminator(data, 0, 0)).isEqualTo(-1);
        }

        @Test
        void searchFromOffset() {
            byte[] data = {0, 'H', 0, 'i'};
            assertThat(ID3FrameParsingUtils.findNullTerminator(data, 1, 0)).isEqualTo(2);
        }
    }

    @Nested
    class GetNullTerminatorSize {

        @Test
        void singleByteEncodings() {
            assertThat(ID3FrameParsingUtils.getNullTerminatorSize(0)).isEqualTo(1);
            assertThat(ID3FrameParsingUtils.getNullTerminatorSize(3)).isEqualTo(1);
        }

        @Test
        void doubleByteEncodings() {
            assertThat(ID3FrameParsingUtils.getNullTerminatorSize(1)).isEqualTo(2);
            assertThat(ID3FrameParsingUtils.getNullTerminatorSize(2)).isEqualTo(2);
        }
    }

    @Nested
    class ParseGenre {

        @Test
        void singleReference() {
            assertThat(ID3FrameParsingUtils.parseGenre("(17)")).isEqualTo("Rock");
        }

        @Test
        void referenceWithText() {
            assertThat(ID3FrameParsingUtils.parseGenre("(17)Rock")).isEqualTo("Rock");
        }

        @Test
        void multipleReferences() {
            assertThat(ID3FrameParsingUtils.parseGenre("(17)(13)")).isEqualTo("Rock; Pop");
        }

        @Test
        void plainText() {
            assertThat(ID3FrameParsingUtils.parseGenre("Rock")).isEqualTo("Rock");
        }

        @Test
        void nullInput_returnsNull() {
            assertThat(ID3FrameParsingUtils.parseGenre(null)).isNull();
        }

        @Test
        void emptyString_returnsEmpty() {
            assertThat(ID3FrameParsingUtils.parseGenre("")).isEmpty();
        }

        @Test
        void mixedReferencesAndText() {
            assertThat(ID3FrameParsingUtils.parseGenre("(17)Rock(13)")).isEqualTo("Rock; Pop");
        }
    }

    @Nested
    class GetPictureTypeDescription {

        @Test
        void knownTypes() {
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(0)).isEqualTo("Other");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(1)).isEqualTo("32x32 file icon");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(2)).isEqualTo("Other file icon");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(3)).isEqualTo("Cover (front)");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(4)).isEqualTo("Cover (back)");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(5)).isEqualTo("Leaflet page");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(6)).isEqualTo("Media");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(7)).isEqualTo("Lead artist");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(8)).isEqualTo("Artist");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(9)).isEqualTo("Conductor");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(10)).isEqualTo("Band");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(11)).isEqualTo("Composer");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(12)).isEqualTo("Lyricist");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(13)).isEqualTo("Recording location");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(14)).isEqualTo("During recording");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(15)).isEqualTo("During performance");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(16)).isEqualTo("Movie screen capture");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(17)).isEqualTo("Bright colored fish");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(18)).isEqualTo("Illustration");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(19)).isEqualTo("Band logotype");
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(20)).isEqualTo("Publisher logotype");
        }

        @Test
        void unknownType() {
            assertThat(ID3FrameParsingUtils.getPictureTypeDescription(99)).isEqualTo("Unknown(99)");
        }
    }
}