package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für Popularimeter Frames (POPM / POP).
 *
 * <p>Popularimeter-Frames enthalten eine E-Mail-Adresse, eine Bewertung (0-255)
 * und einen optionalen Zähler. Die Bewertung wird als Bruch "Rating:N/255" dargestellt.
 * Der Zähler wird nur angegeben wenn er größer als 0 ist.</p>
 *
 * <p>Der Aufbau ist: E-Mail (null-terminiert) | Bewertung (1 Byte) | Zähler (4 Bytes, optional).</p>
 */
public class PopularimeterFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines Popularimeter-Frames und gibt die Bewertungsinformation zurück.
     *
     * <p>Das Ergebnis enthält E-Mail, Bewertung und ggf. Zähler im Format:
     * "E-Mail,Rating:N/255,Count:M"</p>
     *
     * @param data         Roh-Frame-Daten (E-Mail null-terminiert, gefolgt von Bewertung und Zähler)
     * @param frameId      Die Frame-ID ("POPM" für ID3v2.3/4 oder "POP" für ID3v2.2)
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Die formatierte Bewertungsinformation, oder ein leerer String bei ungültigen Daten
     */
    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 2) {
            return "";
        }
        int emailEnd = ID3FrameParsingUtils.findNullTerminator(data, 0, ID3FrameParsingUtils.ISO_8859_1);
        if (emailEnd == -1) {
            return "";
        }

        String email = new String(data, 0, emailEnd, StandardCharsets.ISO_8859_1);
        int pos = emailEnd + 1;

        if (pos >= data.length) {
            return email;
        }

        int rating = data[pos++] & 0xFF;
        long counter = 0;
        if (pos < data.length) {
            for (int i = pos; i < Math.min(data.length, pos + 8); i++) {
                counter = (counter << 8) | (data[i] & 0xFF);
            }
        }

        String result = "Rating:" + rating + "/255";
        if (!email.isEmpty()) {
            result = email + "," + result;
        }
        if (counter > 0) {
            result += ",Count:" + counter;
        }
        return result;
    }
}