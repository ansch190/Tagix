package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.UrlFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class UrlFrameParserTest {

    private final UrlFrameParser parser = new UrlFrameParser();

    @Test
    void parseWxxx_descriptionAndUrl() {
        byte[] data = {0, 'L', 'i', 'n', 'k', 0, 'h', 't', 't', 'p', ':', '/', '/', 'e', 'x', '.', 'c', 'o', 'm'};
        assertThat(parser.parse(data, "WXXX", 3)).isEqualTo("Link: http://ex.com");
    }

    @Test
    void parseWxxx_emptyDescription_returnsUrlOnly() {
        byte[] data = {0, 0, 'h', 't', 't', 'p', ':', '/', '/', 'e', 'x', '.', 'c', 'o', 'm'};
        assertThat(parser.parse(data, "WXXX", 3)).isEqualTo("http://ex.com");
    }

    @Test
    void parseRegularUrlFrame_directIso8859() {
        byte[] data = "http://www.example.com".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(parser.parse(data, "WCOM", 3)).isEqualTo("http://www.example.com");
    }

    @Test
    void parseWxxx_tooShortData_returnsEmptyString() {
        assertThat(parser.parse(new byte[]{0}, "WXXX", 3)).isEmpty();
    }

    @Test
    void parseWxxx_noNullTerminatorForDescription_returnsEmptyString() {
        byte[] data = {0, 'L', 'i', 'n', 'k'};
        assertThat(parser.parse(data, "WXXX", 3)).isEmpty();
    }

    @Test
    void parseWxxx_v22Alias() {
        byte[] data = {0, 'L', 'i', 'n', 'k', 0, 'h', 't', 't', 'p', ':', '/', '/', 'a', '.', 'c', 'o', 'm'};
        assertThat(parser.parse(data, "WXX", 2)).isEqualTo("Link: http://a.com");
    }

    @Test
    void parseWCOP_trimmed() {
        byte[] data = "  http://copyright.com  ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(parser.parse(data, "WCOP", 3)).isEqualTo("http://copyright.com");
    }

    @Test
    void parseWxxx_descriptionOnly_noUrl() {
        byte[] data = {0, 'L', 'i', 'n', 'k', 0};
        assertThat(parser.parse(data, "WXXX", 3)).isEqualTo("Link");
    }
}