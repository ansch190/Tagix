package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für Play Counter Frames (PCNT / CNT).
 */
public class PlayCounterFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length == 0) {
            return "";
        }
        long playCount = 0;
        for (int i = 0; i < Math.min(data.length, 8); i++) {
            playCount = (playCount << 8) | (data[i] & 0xFF);
        }
        return String.valueOf(playCount);
    }
}
