package com.schwanitz.strategies.parsing.id3;

/**
 * Parser für Comment-Frames (COMM / COM).
 *
 * <p>Comment-Frames haben folgende Struktur: Kodierungsbyte | Sprache (3 Bytes) |
 * Beschreibung (null-terminiert) | Kommentartext. Dieser Parser extrahiert den
 * Kommentartext und ignoriert die Beschreibung, da die Sprache und Beschreibung
 * in der Anzeige nicht relevant sind.</p>
 */
public class CommentFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines Comment-Frames und gibt den Kommentartext zurück.
     *
     * <p>Der Aufbau ist: Kodierungsbyte (1 Byte) | Sprache (3 Bytes) |
     * Beschreibung (null-terminiert) | Kommentartext. Die Sprache und Beschreibung
     * werden übersprungen; nur der eigentliche Kommentartext wird zurückgegeben.</p>
     *
     * @param data         Roh-Frame-Daten inklusive Kodierungsbyte und Sprachcode
     * @param frameId      Die Frame-ID ("COMM" für ID3v2.3/4 oder "COM" für ID3v2.2)
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Der dekodierte Kommentartext, oder ein leerer String wenn die Daten ungültig oder zu kurz sind
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

        byte[] commentData = new byte[data.length - textStart];
        System.arraycopy(data, textStart, commentData, 0, commentData.length);
        return ID3FrameParsingUtils.decodeText(commentData, encoding, majorVersion);
    }
}