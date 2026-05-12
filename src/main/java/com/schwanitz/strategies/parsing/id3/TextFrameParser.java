package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für ID3v2 Text-Frames (alle Frame-IDs die mit 'T' beginnen, außer TXXX/TXX).
 * Handhabt auch die Genre-Spezialbehandlung für TCON/TCO.
 *
 * <p>Dieser Parser liest das Kodierungsbyte am Anfang der Frame-Daten, dekodiert den
 * verbleibenden Text entsprechend und wendet bei Genre-Frames (TCON/TCO) automatisch
 * die Genre-Referenzauflösung an.</p>
 */
public class TextFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines Text-Frames und gibt den dekodierten Text zurück.
     *
     * <p>Für Genre-Frames (TCON/TCO) werden nummerische Referenzen in Klammern
     * automatisch zu den entsprechenden Genre-Namen aufgelöst.</p>
     *
     * @param data         Roh-Frame-Daten inklusive Kodierungsbyte
     * @param frameId      Die Frame-ID (z.B. "TIT2", "TALB", "TCON")
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Der dekodierte Textwert; bei Genre-Frames der aufgelöste Genre-String;
     *         ein leerer String wenn die Daten zu kurz sind
     */
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