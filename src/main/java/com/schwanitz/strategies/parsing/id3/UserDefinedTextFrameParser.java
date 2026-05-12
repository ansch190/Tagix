package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für User-Defined Text Frames (TXXX / TXX).
 */
public class UserDefinedTextFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
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

        int valueStart = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
        if (valueStart >= data.length) {
            return description;
        }

        byte[] valueData = new byte[data.length - valueStart];
        System.arraycopy(data, valueStart, valueData, 0, valueData.length);
        String value = ID3FrameParsingUtils.decodeText(valueData, encoding, majorVersion);

        return description.isEmpty() ? value : description + ": " + value;
    }
}
