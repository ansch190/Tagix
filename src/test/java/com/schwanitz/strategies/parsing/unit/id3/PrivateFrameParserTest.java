package com.schwanitz.strategies.parsing.unit.id3;

import com.schwanitz.strategies.parsing.id3.PrivateFrameParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("id3")
class PrivateFrameParserTest {

    private final PrivateFrameParser parser = new PrivateFrameParser();

    @Test
    void parseOwnerAndPrivateData() {
        byte[] ownerBytes = "SuperTag".getBytes(StandardCharsets.ISO_8859_1);
        byte[] privateData = {0x01, 0x02, 0x03, 0x04};
        byte[] data = new byte[ownerBytes.length + 1 + privateData.length];
        System.arraycopy(ownerBytes, 0, data, 0, ownerBytes.length);
        data[ownerBytes.length] = 0;
        System.arraycopy(privateData, 0, data, ownerBytes.length + 1, privateData.length);

        assertThat(parser.parse(data, "PRIV", 3)).isEqualTo("[PRIVATE:SuperTag,4 bytes]");
    }

    @Test
    void parseNoNullTerminator() {
        byte[] data = "NoNullTerminator".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(parser.parse(data, "PRIV", 3)).isEqualTo("[PRIVATE:16 bytes]");
    }

    @Test
    void parseEmptyData_returnsEmptyString() {
        assertThat(parser.parse(new byte[0], "PRIV", 3)).isEmpty();
    }

    @Test
    void parseEmptyPrivateData() {
        byte[] ownerBytes = "Owner".getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[ownerBytes.length + 1];
        System.arraycopy(ownerBytes, 0, data, 0, ownerBytes.length);
        data[ownerBytes.length] = 0;

        assertThat(parser.parse(data, "PRIV", 3)).isEqualTo("[PRIVATE:Owner,0 bytes]");
    }
}