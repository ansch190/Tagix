package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für URL-Frames (alle die mit 'W' beginnen, inkl. WXXX/WXX).
 *
 * <p>Dieser Parser unterscheidet zwischen benutzerdefinierten URL-Frames (WXXX/WXX),
 * die ein Kodierungsbyte, eine Beschreibung und eine URL enthalten, und einfachen
 * URL-Frames (wie WCOM, WCOP, etc.), die direkt als ISO-8859-1-kodierter Text vorliegen.</p>
 *
 * <p>Für WXXX/WXX-Frames wird das Format "Beschreibung: URL" zurückgegeben,
 * wenn eine Beschreibung vorhanden ist, andernfalls nur die URL.</p>
 */
public class UrlFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines URL-Frames und gibt die URL oder eine formatierte Beschreibung-URL zurück.
     *
     * <p>Für WXXX/WXX-Frames werden Beschreibung und URL separat extrahiert und
     * formatiert. Für alle anderen URL-Frames werden die Daten direkt als
     * ISO-8859-1-Text dekodiert.</p>
     *
     * @param data         Roh-Frame-Daten (für WXXX/WXX inklusive Kodierungsbyte, sonst reine URL-Daten)
     * @param frameId      Die Frame-ID (z.B. "WXXX", "WCOM", "WCOP")
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Die URL oder der formatierte String "Beschreibung: URL";
     *         ein leerer String bei ungültigen oder zu kurzen Daten
     */
    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if ("WXXX".equals(frameId) || "WXX".equals(frameId)) {
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

            int urlStart = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
            if (urlStart >= data.length) {
                return description;
            }

            byte[] urlData = new byte[data.length - urlStart];
            System.arraycopy(data, urlStart, urlData, 0, urlData.length);
            String url = new String(urlData, StandardCharsets.ISO_8859_1).trim();

            return description.isEmpty() ? url : description + ": " + url;
        }

        // Standard URL-Frames sind direkt ISO-8859-1
        return new String(data, StandardCharsets.ISO_8859_1).trim();
    }
}