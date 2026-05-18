package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.CommentFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class CommentFrameParserTest {

    private final CommentFrameParser parser = new CommentFrameParser();

    @Test
    void parseStandardComm_iso8859() {
        byte[] data = {0, 'e', 'n', 'g', 'M', 'y', ' ', 'D', 'e', 's', 'c', 0, 'H', 'e', 'l', 'l', 'o'};
        assertThat(parser.parse(data, "COMM", 3)).isEqualTo("Hello");
    }

    @Test
    void parseComm_emptyDescription() {
        byte[] data = {0, 'e', 'n', 'g', 0, 'H', 'e', 'l', 'l', 'o'};
        assertThat(parser.parse(data, "COMM", 3)).isEqualTo("Hello");
    }

    @Test
    void parseComm_utf8_v24() {
        byte[] data = {3, 'e', 'n', 'g', 0, 'H', 'e', 'l', 'l', 'o'};
        assertThat(parser.parse(data, "COMM", 4)).isEqualTo("Hello");
    }

    @Test
    void parseComm_tooShortData_returnsEmptyString() {
        assertThat(parser.parse(new byte[]{0, 1, 2}, "COMM", 3)).isEmpty();
        assertThat(parser.parse(new byte[0], "COMM", 3)).isEmpty();
    }

    @Test
    void parseComm_v22Alias() {
        byte[] data = {0, 'e', 'n', 'g', 0, 'C', 'o', 'm', 'm', 'e', 'n', 't'};
        assertThat(parser.parse(data, "COM", 2)).isEqualTo("Comment");
    }

    @Test
    void parseComm_iso8859_withDescription() {
        byte[] data = {0, 'e', 'n', 'g', 'D', 'e', 's', 'c', 0, 'C', 'o', 'm', 'm', 'e', 'n', 't'};
        assertThat(parser.parse(data, "COMM", 3)).isEqualTo("Comment");
    }

    @Test
    void parseComm_noCommentAfterDescription() {
        byte[] data = {0, 'e', 'n', 'g', 'D', 0};
        assertThat(parser.parse(data, "COMM", 3)).isEmpty();
    }
}