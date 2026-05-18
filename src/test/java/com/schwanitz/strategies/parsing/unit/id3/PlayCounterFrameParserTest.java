package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.PlayCounterFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class PlayCounterFrameParserTest {

    private final PlayCounterFrameParser parser = new PlayCounterFrameParser();

    @Test
    void parse4ByteCounter() {
        byte[] data = {0, 0, 0, 42};
        assertThat(parser.parse(data, "PCNT", 3)).isEqualTo("42");
    }

    @Test
    void parse8ByteCounter() {
        byte[] data = {0, 0, 0, 0, 0, 0, 1, 0};
        assertThat(parser.parse(data, "PCNT", 3)).isEqualTo("256");
    }

    @Test
    void parse1ByteCounter() {
        byte[] data = {42};
        assertThat(parser.parse(data, "PCNT", 3)).isEqualTo("42");
    }

    @Test
    void parseEmptyData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "PCNT", 3)).isEmpty();
    }

    @Test
    void parseLarge8ByteCounter() {
        byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                       (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertThat(parser.parse(data, "PCNT", 3)).isEqualTo(String.valueOf(0xFFFFFFFFFFFFFFFFL));
    }

    @Test
    void parse_v22Alias() {
        byte[] data = {0, 0, 0, 7};
        assertThat(parser.parse(data, "CNT", 2)).isEqualTo("7");
    }

    @Test
    void parse2ByteCounter() {
        byte[] data = {0, 100};
        assertThat(parser.parse(data, "PCNT", 3)).isEqualTo("100");
    }
}