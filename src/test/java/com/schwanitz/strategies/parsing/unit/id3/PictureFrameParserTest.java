package com.schwanitz.strategies.presentation.unit.id3;

import com.schwanitz.strategies.parsing.id3.PictureFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class PictureFrameParserTest {

    private final PictureFrameParser parser = new PictureFrameParser();

    @Test
    void parseApic_jpeg() {
        byte[] imageData = {(byte) 0xFF, (byte) 0xD8, 0x01, 0x02, 0x03};
        byte[] data = buildApicData(0, "image/jpeg", (byte) 3, "Front", imageData);

        String result = parser.parse(data, "APIC", 3);
        assertThat(result).startsWith("[PICTURE:image/jpeg,Cover (front),");
        assertThat(result).contains("desc:Front");
        assertThat(result).contains("bytes");
    }

    @Test
    void parseApic_png() {
        byte[] imageData = {(byte) 0x89, 0x50, 0x4E, 0x47};
        byte[] data = buildApicData(0, "image/png", (byte) 3, "Cover", imageData);

        String result = parser.parse(data, "APIC", 3);
        assertThat(result).startsWith("[PICTURE:image/png,Cover (front),");
        assertThat(result).contains("desc:Cover");
    }

    @Test
    void parsePic_v22() {
        byte[] imageData = {0x01, 0x02, 0x03, 0x04, 0x05};
        byte[] data = buildPicData(0, "JPG", (byte) 3, "Front", imageData);

        String result = parser.parse(data, "PIC", 2);
        assertThat(result).startsWith("[PICTURE:JPG,Cover (front),");
        assertThat(result).contains("5 bytes");
        assertThat(result).contains("desc:Front");
    }

    @Test
    void parseTooShortData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "APIC", 3)).isEmpty();
        assertThat(parser.parse(new byte[]{0}, "APIC", 3)).isEmpty();
    }

    @Test
    void parseApic_invalidMimeType_noNullTerminator() {
        byte[] data = {0, 'i', 'm', 'a', 'g', 'e', '/', 'j', 'p', 'e', 'g'}; // no null terminator
        String result = parser.parse(data, "APIC", 3);
        assertThat(result).isEqualTo("[PICTURE: Invalid MIME type]");
    }

    @Test
    void parseApic_emptyDescription() {
        byte[] imageData = {0x01, 0x02, 0x03};
        byte[] data = buildApicData(0, "image/jpeg", (byte) 3, "", imageData);

        String result = parser.parse(data, "APIC", 3);
        assertThat(result).doesNotContain("desc:");
    }

    @Test
    void parseApic_noImageData() {
        byte[] mimeBytes = "image/jpeg".getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[1 + mimeBytes.length + 1 + 1 + 1]; // encoding + mime + null + picType + descNull
        data[0] = 0;
        System.arraycopy(mimeBytes, 0, data, 1, mimeBytes.length);
        data[1 + mimeBytes.length] = 0; // null terminator for MIME
        data[1 + mimeBytes.length + 1] = 3; // picture type
        data[data.length - 1] = 0; // null terminator for description

        String result = parser.parse(data, "APIC", 3);
        assertThat(result).contains("no data");
    }

    @Test
    void parseApic_largeImageData_includesPreview() {
        byte[] imageData = new byte[150];
        for (int i = 0; i < imageData.length; i++) imageData[i] = (byte) i;
        byte[] data = buildApicData(0, "image/jpeg", (byte) 3, "", imageData);

        String result = parser.parse(data, "APIC", 3);
        assertThat(result).contains("150 bytes");
        assertThat(result).contains("preview:");
        assertThat(result).contains("...");
    }

    @Test
    void parsePic_tooShortData() {
        assertThat(parser.parse(new byte[]{0, 'J', 'P'}, "PIC", 2)).isEqualTo("[PICTURE: Invalid PIC frame]");
    }

    private byte[] buildApicData(int encoding, String mimeType, byte pictureType,
                                  String description, byte[] imageData) {
        byte[] mimeBytes = mimeType.getBytes(StandardCharsets.ISO_8859_1);
        byte[] descBytes = description.getBytes(StandardCharsets.ISO_8859_1);
        int totalLen = 1 + mimeBytes.length + 1 + 1 + descBytes.length + 1 + imageData.length;
        byte[] data = new byte[totalLen];
        int pos = 0;
        data[pos++] = (byte) encoding;
        System.arraycopy(mimeBytes, 0, data, pos, mimeBytes.length);
        pos += mimeBytes.length;
        data[pos++] = 0; // null terminator for MIME
        data[pos++] = pictureType;
        System.arraycopy(descBytes, 0, data, pos, descBytes.length);
        pos += descBytes.length;
        data[pos++] = 0; // null terminator for description
        System.arraycopy(imageData, 0, data, pos, imageData.length);
        return data;
    }

    private byte[] buildPicData(int encoding, String format, byte pictureType,
                                String description, byte[] imageData) {
        byte[] formatBytes = format.getBytes(StandardCharsets.ISO_8859_1);
        byte[] descBytes = description.getBytes(StandardCharsets.ISO_8859_1);
        int totalLen = 1 + 3 + 1 + descBytes.length + 1 + imageData.length;
        byte[] data = new byte[totalLen];
        int pos = 0;
        data[pos++] = (byte) encoding;
        System.arraycopy(formatBytes, 0, data, pos, 3);
        pos += 3;
        data[pos++] = pictureType;
        System.arraycopy(descBytes, 0, data, pos, descBytes.length);
        pos += descBytes.length;
        data[pos++] = 0; // null terminator for description
        System.arraycopy(imageData, 0, data, pos, imageData.length);
        return data;
    }
}