package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.LyricsFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class LyricsFrameParserTest {

    private final LyricsFrameParser parser = new LyricsFrameParser();

    @Test
    void parseStandardUslt_iso8859() {
        byte[] data = {0, 'e', 'n', 'g', 'V', 'e', 'r', 's', 'e', 0, 'L', 'a', ' ', 'l', 'a', ' ', 'l', 'a'};
        assertThat(parser.parse(data, "USLT", 3)).isEqualTo("La la la");
    }

    @Test
    void parseUslt_emptyDescription() {
        byte[] data = {0, 'e', 'n', 'g', 0, 'L', 'y', 'r', 'i', 'c', 's'};
        assertThat(parser.parse(data, "USLT", 3)).isEqualTo("Lyrics");
    }

    @Test
    void parseTooShortData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "USLT", 3)).isEmpty();
        assertThat(parser.parse(new byte[]{0, 'e', 'n'}, "USLT", 3)).isEmpty();
    }

    @Test
    void parseUslt_utf8_v24() {
        byte[] data = {3, 'e', 'n', 'g', 0, 'S', 'o', 'n', 'g', ' ', 't', 'e', 'x', 't'};
        assertThat(parser.parse(data, "USLT", 4)).isEqualTo("Song text");
    }

    @Test
    void parseUslt_v22Alias() {
        byte[] data = {0, 'e', 'n', 'g', 0, 'L', 'y', 'r', 'i', 'c', 's'};
        assertThat(parser.parse(data, "ULT", 2)).isEqualTo("Lyrics");
    }

    @Test
    void parseUslt_descriptionOnly_noLyrics() {
        byte[] data = {0, 'e', 'n', 'g', 'D', 'e', 's', 'c', 0};
        assertThat(parser.parse(data, "USLT", 3)).isEmpty();
    }
}