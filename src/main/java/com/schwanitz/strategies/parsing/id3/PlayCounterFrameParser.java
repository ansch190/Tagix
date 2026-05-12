package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für Play Counter Frames (PCNT / CNT).
 *
 * <p>Play Counter Frames enthalten einen einzelnen Zählerwert, der die Anzahl
 * der Wiedergaben repräsentiert. Der Zähler wird als vorzeichenloser Integer
 * mit bis zu 8 Bytes (64 Bit) gespeichert.</p>
 */
public class PlayCounterFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines Play-Counter-Frames und gibt den Zählerwert als String zurück.
     *
     * <p>Die Daten bestehen aus einem vorzeichenlosen Integer-Wert mit bis zu 8 Bytes Länge.
     * Die Bytes werden Big-Endian (Most Significant Byte first) interpretiert.</p>
     *
     * @param data         Roh-Frame-Daten (der Zählerwert als Big-Endian Bytes)
     * @param frameId      Die Frame-ID ("PCNT" für ID3v2.3/4 oder "CNT" für ID3v2.2)
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Der Zählerwert als String, oder ein leerer String bei leeren Daten
     */
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