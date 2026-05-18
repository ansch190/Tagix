package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.TextFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class TextFrameParserTest {

    private final TextFrameParser parser = new TextFrameParser();

    @Test
    void parseEmptyData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "TIT2", 3)).isEmpty();
    }

    @Test
    void parseIso8859Text_v23() {
        byte[] data = {0, 'H', 'e', 'l', 'l', 'o'};
        assertThat(parser.parse(data, "TIT2", 3)).isEqualTo("Hello");
    }

    @Test
    void parseUtf16Text_v23() {
        byte[] textBytes = "Hello".getBytes(StandardCharsets.UTF_16);
        byte[] data = new byte[1 + textBytes.length];
        data[0] = 1;
        System.arraycopy(textBytes, 0, data, 1, textBytes.length);

        assertThat(parser.parse(data, "TIT2", 3)).isEqualTo("Hello");
    }

    @Test
    void parseUtf16BEText_v23() {
        byte[] textBytes = "Hello".getBytes(StandardCharsets.UTF_16BE);
        byte[] data = new byte[1 + textBytes.length];
        data[0] = 2;
        System.arraycopy(textBytes, 0, data, 1, textBytes.length);

        assertThat(parser.parse(data, "TIT2", 3)).isEqualTo("Hello");
    }

    @Test
    void parseUtf8Text_v24() {
        byte[] data = {3, 'H', 'e', 'l', 'l', 'o'};
        assertThat(parser.parse(data, "TIT2", 4)).isEqualTo("Hello");
    }

    @Test
    void parseUtf8Text_v23_fallsBackToIso8859() {
        byte[] data = {3, 'H', 'e', 'l', 'l', 'o'};
        assertThat(parser.parse(data, "TIT2", 3)).isEqualTo("Hello");
    }

    @Test
    void parseGenre_singleReference() {
        byte[] data = {0, '(', '1', '7', ')'};
        assertThat(parser.parse(data, "TCON", 3)).isEqualTo("Rock");
    }

    @Test
    void parseGenre_referenceWithText() {
        byte[] data = {0, '(', '1', '7', ')', 'R', 'o', 'c', 'k'};
        assertThat(parser.parse(data, "TCON", 3)).isEqualTo("Rock");
    }

    @Test
    void parseGenre_multipleReferences() {
        byte[] data = {0, '(', '1', '7', ')', '(', '1', '3', ')'};
        assertThat(parser.parse(data, "TCON", 3)).isEqualTo("Rock; Pop");
    }

    @Test
    void parseGenre_v22Alias() {
        byte[] data = {0, '(', '1', '7', ')'};
        assertThat(parser.parse(data, "TCO", 2)).isEqualTo("Rock");
    }

    @Test
    void parseNonGenreFrame_ignoresGenreResolution() {
        byte[] data = {0, '(', '1', '7', ')'};
        assertThat(parser.parse(data, "TIT2", 3)).isEqualTo("(17)");
    }

    @Test
    void parseUnknownEncoding_fallsBackToIso8859() {
        byte[] data = {99, 'H', 'e', 'l', 'l', 'o'};
        assertThat(parser.parse(data, "TIT2", 3)).isEqualTo("Hello");
    }

    @Test
    void parseIso8859Text_trimsWhitespace() {
        byte[] data = {0, ' ', 'H', 'i', ' '};
        assertThat(parser.parse(data, "TIT2", 3)).isEqualTo("Hi");
    }

    @Test
    void parseOnlyEncodingByte_returnsEmpty() {
        byte[] data = {0};
        assertThat(parser.parse(data, "TIT2", 3)).isEmpty();
    }
}