package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für URL-Frames (alle die mit 'W' beginnen, inkl. WXXX/WXX).
 */
public class UrlFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if ("WXXX".equals(frameId) || "WXX".equals(frameId)) {
            if (data.length < 2) {
                return "";
            }
            int encoding = data[0] & 0xFF;
            int descEnd = ID3FrameParsingUtils.findNullTerminator(data, 1, encoding);
            if (descEnd == -1) {
                return "";
            }

            byte[] descData = new byte[descEnd - 1];
            System.arraycopy(data, 1, descData, 0, descData.length);
            String description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);

            int urlStart = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
            if (urlStart >= data.length) {
                return description;
            }

            byte[] urlData = new byte[data.length - urlStart];
            System.arraycopy(data, urlStart, urlData, 0, urlData.length);
            String url = new String(urlData, StandardCharsets.ISO_8859_1).trim();

            return description.isEmpty() ? url : description + ": " + url;
        }

        // Standard URL-Frames sind direkt ISO-8859-1
        return new String(data, StandardCharsets.ISO_8859_1).trim();
    }
}
