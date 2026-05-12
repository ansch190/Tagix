package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für Unsynchronised Lyrics Frames (USLT / ULT).
 */
public class LyricsFrameParser implements ID3FrameParser {

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

        byte[] lyricsData = new byte[data.length - textStart];
        System.arraycopy(data, textStart, lyricsData, 0, lyricsData.length);
        return ID3FrameParsingUtils.decodeText(lyricsData, encoding, majorVersion);
    }
}
