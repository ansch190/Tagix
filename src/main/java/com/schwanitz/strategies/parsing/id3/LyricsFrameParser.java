package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für Unsynchronised Lyrics Frames (USLT / ULT).
 *
 * <p>Lyrics-Frames haben dieselbe Struktur wie Comment-Frames: Kodierungsbyte |
 * Sprache (3 Bytes) | Beschreibung (null-terminiert) | Liedtext.
 * Dieser Parser extrahiert den Liedtext und ignoriert die Beschreibung.</p>
 */
public class LyricsFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines Lyrics-Frames und gibt den Liedtext zurück.
     *
     * <p>Der Aufbau ist: Kodierungsbyte (1 Byte) | Sprache (3 Bytes) |
     * Beschreibung (null-terminiert) | Liedtext. Die Sprache und Beschreibung
     * werden übersprungen; nur der eigentliche Liedtext wird zurückgegeben.</p>
     *
     * @param data         Roh-Frame-Daten inklusive Kodierungsbyte und Sprachcode
     * @param frameId      Die Frame-ID ("USLT" für ID3v2.3/4 oder "ULT" für ID3v2.2)
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Der dekodierte Liedtext, oder ein leerer String wenn die Daten ungültig oder zu kurz sind
     */
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