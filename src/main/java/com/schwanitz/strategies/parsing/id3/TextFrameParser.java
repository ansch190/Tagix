package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für ID3v2 Text-Frames (alle Frame-IDs die mit 'T' beginnen, außer TXXX/TXX).
 * Handhabt auch die Genre-Spezialbehandlung für TCON/TCO.
 */
public class TextFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 1) {
            return "";
        }
        int encoding = data[0] & 0xFF;
        byte[] textData = new byte[data.length - 1];
        System.arraycopy(data, 1, textData, 0, textData.length);
        String text = ID3FrameParsingUtils.decodeText(textData, encoding, majorVersion);

        if ("TCON".equals(frameId) || "TCO".equals(frameId)) {
            text = ID3FrameParsingUtils.parseGenre(text);
        }
        return text;
    }
}
