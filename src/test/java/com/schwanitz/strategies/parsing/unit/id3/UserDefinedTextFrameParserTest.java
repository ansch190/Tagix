package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.UserDefinedTextFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class UserDefinedTextFrameParserTest {

    private final UserDefinedTextFrameParser parser = new UserDefinedTextFrameParser();

    @Test
    void parseEmptyData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "TXXX", 3)).isEmpty();
    }

    @Test
    void parseTooShortData_returnsEmptyString() {
        assertThat(parser.parse(new byte[]{0}, "TXXX", 3)).isEmpty();
    }

    @Test
    void parseIso8859_descriptionAndValue() {
        byte[] data = {0, 'G', 'e', 'n', 'r', 'e', 0, 'R', 'o', 'c', 'k'};
        assertThat(parser.parse(data, "TXXX", 3)).isEqualTo("Genre: Rock");
    }

    @Test
    void parseUtf16_descriptionAndValue() {
        byte[] descBytes = "desc".getBytes(StandardCharsets.UTF_16);
        byte[] valueBytes = "value".getBytes(StandardCharsets.UTF_16);
        byte[] data = new byte[1 + descBytes.length + 2 + valueBytes.length];
        data[0] = 1;
        System.arraycopy(descBytes, 0, data, 1, descBytes.length);
        data[1 + descBytes.length] = 0;
        data[1 + descBytes.length + 1] = 0;
        System.arraycopy(valueBytes, 0, data, 1 + descBytes.length + 2, valueBytes.length);

        assertThat(parser.parse(data, "TXXX", 3)).isEqualTo("desc: value");
    }

    @Test
    void parseEmptyDescription_returnsValueOnly() {
        byte[] data = {0, 0, 'R', 'o', 'c', 'k'};
        assertThat(parser.parse(data, "TXXX", 3)).isEqualTo("Rock");
    }

    @Test
    void parseNoNullTerminatorForDescription_returnsEmptyString() {
        byte[] data = {0, 'G', 'e', 'n', 'r', 'e'};
        assertThat(parser.parse(data, "TXXX", 3)).isEmpty();
    }

    @Test
    void parseDescriptionOnly_noValue() {
        byte[] data = {0, 'G', 'e', 'n', 'r', 'e', 0};
        assertThat(parser.parse(data, "TXXX", 3)).isEqualTo("Genre");
    }

    @Test
    void parse_v22Alias() {
        byte[] data = {0, 'G', 'e', 'n', 'r', 'e', 0, 'R', 'o', 'c', 'k'};
        assertThat(parser.parse(data, "TXX", 2)).isEqualTo("Genre: Rock");
    }
}