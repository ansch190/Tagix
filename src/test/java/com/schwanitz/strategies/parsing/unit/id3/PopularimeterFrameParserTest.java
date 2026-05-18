package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.PopularimeterFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class PopularimeterFrameParserTest {

    private final PopularimeterFrameParser parser = new PopularimeterFrameParser();

    @Test
    void parseEmail_ratingWithCounter() {
        byte[] emailBytes = "user@example.com".getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[emailBytes.length + 1 + 1 + 4];
        System.arraycopy(emailBytes, 0, data, 0, emailBytes.length);
        int pos = emailBytes.length;
        data[pos++] = 0; // null terminator
        data[pos++] = (byte) 196; // rating
        data[pos++] = 0; data[pos++] = 0; data[pos++] = 0; data[pos++] = 42; // counter

        assertThat(parser.parse(data, "POPM", 3)).isEqualTo("user@example.com,Rating:196/255,Count:42");
    }

    @Test
    void parseEmptyEmail_rating() {
        byte[] data = {0, (byte) 128};
        assertThat(parser.parse(data, "POPM", 3)).isEqualTo("Rating:128/255");
    }

    @Test
    void parseEmail_ratingWithZeroCounter_omitsCounter() {
        byte[] emailBytes = "user@example.com".getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[emailBytes.length + 1 + 1 + 4];
        System.arraycopy(emailBytes, 0, data, 0, emailBytes.length);
        int pos = emailBytes.length;
        data[pos++] = 0;
        data[pos++] = (byte) 196;
        data[pos++] = 0; data[pos++] = 0; data[pos++] = 0; data[pos++] = 0;

        assertThat(parser.parse(data, "POPM", 3)).isEqualTo("user@example.com,Rating:196/255");
    }

    @Test
    void parseTooShortData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "POPM", 3)).isEmpty();
        assertThat(parser.parse(new byte[]{0}, "POPM", 3)).isEmpty();
    }

    @Test
    void parseEmailOnly_returnsEmail() {
        byte[] emailBytes = "user@example.com".getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[emailBytes.length + 1];
        System.arraycopy(emailBytes, 0, data, 0, emailBytes.length);
        data[emailBytes.length] = 0;

        assertThat(parser.parse(data, "POPM", 3)).isEqualTo("user@example.com");
    }

    @Test
    void parseNoNullTerminator_returnsEmptyString() {
        byte[] data = "user@example.com".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(parser.parse(data, "POPM", 3)).isEmpty();
    }

    @Test
    void parse_v22Alias() {
        byte[] data = {0, (byte) 255};
        assertThat(parser.parse(data, "POP", 2)).isEqualTo("Rating:255/255");
    }

    @Test
    void parseLargeCounter() {
        byte[] emailBytes = "u@e.com".getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[emailBytes.length + 1 + 1 + 8];
        System.arraycopy(emailBytes, 0, data, 0, emailBytes.length);
        int pos = emailBytes.length;
        data[pos++] = 0;
        data[pos++] = (byte) 128;
        data[pos++] = 0; data[pos++] = 0; data[pos++] = 0; data[pos++] = 0;
        data[pos++] = 0; data[pos++] = 0; data[pos++] = 0; data[pos] = 42;

        assertThat(parser.parse(data, "POPM", 3)).isEqualTo("u@e.com,Rating:128/255,Count:42");
    }
}