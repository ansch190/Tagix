package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für Private Frames (PRIV).
 */
public class PrivateFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length == 0) {
            return "";
        }
        int ownerEnd = ID3FrameParsingUtils.findNullTerminator(data, 0, ID3FrameParsingUtils.ISO_8859_1);
        if (ownerEnd == -1) {
            return "[PRIVATE:" + data.length + " bytes]";
        }

        String owner = new String(data, 0, ownerEnd, StandardCharsets.ISO_8859_1);
        int dataSize = data.length - ownerEnd - 1;
        return "[PRIVATE:" + owner + "," + dataSize + " bytes]";
    }
}
