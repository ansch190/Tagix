package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für Comment-Frames (COMM / COM).
 */
public class CommentFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 4) {
            return "";
        }
        int encoding = data[0] & 0xFF;
        int textStart = 4; // Sprache überspringen

        int descEnd = ID3FrameParsingUtils.findNullTerminator(data, textStart, encoding);
        if (descEnd != -1) {
            textStart = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
        }

        if (textStart >= data.length) {
            return "";
        }

        byte[] commentData = new byte[data.length - textStart];
        System.arraycopy(data, textStart, commentData, 0, commentData.length);
        return ID3FrameParsingUtils.decodeText(commentData, encoding, majorVersion);
    }
}
