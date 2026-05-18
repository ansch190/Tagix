package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.GeneralObjectFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class GeneralObjectFrameParserTest {

    private final GeneralObjectFrameParser parser = new GeneralObjectFrameParser();

    @Test
    void parseGeob_fullData() {
        byte[] mimeBytes = "application/pdf".getBytes(StandardCharsets.ISO_8859_1);
        byte[] filenameBytes = "test.pdf".getBytes(StandardCharsets.ISO_8859_1);
        byte[] descBytes = "My Doc".getBytes(StandardCharsets.ISO_8859_1);
        byte[] objectData = {0x01, 0x02, 0x03, 0x04, 0x05};

        int totalLen = 1 + mimeBytes.length + 1 + filenameBytes.length + 1 + descBytes.length + 1 + objectData.length;
        byte[] data = new byte[totalLen];
        int pos = 0;
        data[pos++] = 0; // ISO-8859-1 encoding
        System.arraycopy(mimeBytes, 0, data, pos, mimeBytes.length); pos += mimeBytes.length;
        data[pos++] = 0; // null terminator for MIME
        System.arraycopy(filenameBytes, 0, data, pos, filenameBytes.length); pos += filenameBytes.length;
        data[pos++] = 0; // null terminator for filename
        System.arraycopy(descBytes, 0, data, pos, descBytes.length); pos += descBytes.length;
        data[pos++] = 0; // null terminator for description
        System.arraycopy(objectData, 0, data, pos, objectData.length);

        String result = parser.parse(data, "GEOB", 3);
        assertThat(result).isEqualTo("[OBJECT:application/pdf,5 bytes,file:test.pdf,desc:My Doc]");
    }

    @Test
    void parseGeob_emptyDescriptionAndFilename() {
        byte[] mimeBytes = "text/plain".getBytes(StandardCharsets.ISO_8859_1);
        byte[] objectData = {0x0A, 0x0B};

        int totalLen = 1 + mimeBytes.length + 1 + 1 + 1 + objectData.length;
        byte[] data = new byte[totalLen];
        int pos = 0;
        data[pos++] = 0;
        System.arraycopy(mimeBytes, 0, data, pos, mimeBytes.length); pos += mimeBytes.length;
        data[pos++] = 0; // null terminator for MIME
        data[pos++] = 0; // null terminator for filename (empty)
        data[pos++] = 0; // null terminator for description (empty)
        System.arraycopy(objectData, 0, data, pos, objectData.length);

        String result = parser.parse(data, "GEOB", 3);
        assertThat(result).isEqualTo("[OBJECT:text/plain,2 bytes]");
    }

    @Test
    void parseTooShortData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "GEOB", 3)).isEmpty();
        assertThat(parser.parse(new byte[]{0}, "GEOB", 3)).isEmpty();
    }

    @Test
    void parseGeob_invalidMimeType_noNullTerminator() {
        byte[] data = {0, 't', 'e', 'x', 't', '/', 'p', 'l', 'a', 'i', 'n'}; // no null terminator
        String result = parser.parse(data, "GEOB", 3);
        assertThat(result).isEqualTo("[OBJECT: Invalid MIME type]");
    }

    @Test
    void parse_v22Alias() {
        byte[] mimeBytes = "app/bin".getBytes(StandardCharsets.ISO_8859_1);
        byte[] objectData = {0x01};

        int totalLen = 1 + mimeBytes.length + 1 + 1 + 1 + objectData.length;
        byte[] data = new byte[totalLen];
        int pos = 0;
        data[pos++] = 0;
        System.arraycopy(mimeBytes, 0, data, pos, mimeBytes.length); pos += mimeBytes.length;
        data[pos++] = 0;
        data[pos++] = 0;
        data[pos++] = 0;
        System.arraycopy(objectData, 0, data, pos, objectData.length);

        String result = parser.parse(data, "GEO", 2);
        assertThat(result).isEqualTo("[OBJECT:app/bin,1 bytes]");
    }
}