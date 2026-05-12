package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für Popularimeter Frames (POPM / POP).
 */
public class PopularimeterFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 2) {
            return "";
        }
        int emailEnd = ID3FrameParsingUtils.findNullTerminator(data, 0, ID3FrameParsingUtils.ISO_8859_1);
        if (emailEnd == -1) {
            return "";
        }

        String email = new String(data, 0, emailEnd, StandardCharsets.ISO_8859_1);
        int pos = emailEnd + 1;

        if (pos >= data.length) {
            return email;
        }

        int rating = data[pos++] & 0xFF;
        long counter = 0;
        if (pos < data.length) {
            for (int i = pos; i < Math.min(data.length, pos + 8); i++) {
                counter = (counter << 8) | (data[i] & 0xFF);
            }
        }

        String result = "Rating:" + rating + "/255";
        if (!email.isEmpty()) {
            result = email + "," + result;
        }
        if (counter > 0) {
            result += ",Count:" + counter;
        }
        return result;
    }
}
