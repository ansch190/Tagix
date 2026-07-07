package com.schwanitz.strategies.parsing.id3;

import com.schwanitz.metadata.PictureData;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Parser für Attached Picture Frames (APIC / PIC).
 *
 * <p>Dieser Parser extrahiert Metadaten aus Bild-Frames einschließlich MIME-Typ,
 * Bildtyp, Beschreibung und einer Base64-kodierten Vorschau der Bilddaten.
 * Die Ausgabe erfolgt in einem strukturierten Format: [PICTURE:MIME-Typ,Bildtyp,Größe bytes,desc:Beschreibung,preview:Base64...]</p>
 *
 * <p>Für APIC-Frames (ID3v2.3/4) wird der MIME-Typ als null-terminierter ISO-8859-1-String gelesen.
 * Für PIC-Frames (ID3v2.2) wird das Bildformat als 3-Zeichen-String gelesen.</p>
 */
public class PictureFrameParser implements ID3FrameParser {

    /**
     * Parst die Rohdaten eines Bild-Frames und gibt eine formatierte Bildinformation zurück.
     *
     * <p>Der Aufbau unterscheidet sich je nach Frame-ID:
     * <ul>
     *   <li>APIC: Kodierungsbyte | MIME-Typ (null-terminiert) | Bildtyp (1 Byte) |
     *       Beschreibung (null-terminiert) | Bilddaten</li>
     *   <li>PIC: Kodierungsbyte | Bildformat (3 Bytes) | Bildtyp (1 Byte) |
     *       Beschreibung (null-terminiert) | Bilddaten</li>
     * </ul></p>
     *
     * @param data         Roh-Frame-Daten inklusive Kodierungsbyte
     * @param frameId      Die Frame-ID ("APIC" für ID3v2.3/4 oder "PIC" für ID3v2.2)
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return Eine formatierte Bildinformation im Format [PICTURE:...] mit MIME-Typ/Bildformat,
     *         Bildtyp-Beschreibung, Datengröße und Base64-Vorschau;
     *         ein leerer String bei ungültigen Daten, oder eine Fehlermeldung bei Parsing-Fehlern
     */
    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 2) {
            return "";
        }

        try {
            int pos = 0;
            int encoding = data[pos++] & 0xFF;

            if ("APIC".equals(frameId)) {
                int mimeEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, ID3FrameParsingUtils.ISO_8859_1);
                if (mimeEnd == -1) {
                    return "[PICTURE: Invalid MIME type]";
                }

                String mimeType = new String(data, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
                pos = mimeEnd + 1;

                if (pos >= data.length) {
                    return "[PICTURE:" + mimeType + "]";
                }

                int pictureType = data[pos++] & 0xFF;
                String pictureTypeStr = ID3FrameParsingUtils.getPictureTypeDescription(pictureType);

                int descEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
                String description = "";
                if (descEnd != -1) {
                    byte[] descData = new byte[descEnd - pos];
                    System.arraycopy(data, pos, descData, 0, descData.length);
                    description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);
                    pos = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
                }

                int pictureDataSize = data.length - pos;
                return buildPictureInfo(mimeType, pictureTypeStr, description, pictureDataSize, data, pos);

            } else if ("PIC".equals(frameId)) {
                if (data.length < 5) {
                    return "[PICTURE: Invalid PIC frame]";
                }

                String imageFormat = new String(data, pos, 3, StandardCharsets.ISO_8859_1);
                pos += 3;

                int pictureType = data[pos++] & 0xFF;
                String pictureTypeStr = ID3FrameParsingUtils.getPictureTypeDescription(pictureType);

                int descEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
                String description = "";
                if (descEnd != -1) {
                    byte[] descData = new byte[descEnd - pos];
                    System.arraycopy(data, pos, descData, 0, descData.length);
                    description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);
                    pos = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
                }

                int pictureDataSize = data.length - pos;
                return buildPictureInfo(imageFormat, pictureTypeStr, description, pictureDataSize, data, pos);
            }
        } catch (Exception e) {
            // Log.warn würde hier stehen, aber wir wollen keine Dependency auf slf4j in jedem Parser
            // Falls nötig, kann das später ergänzt werden
        }

        return "[PICTURE:" + data.length + " bytes]";
    }

    /**
     * Parst die Rohdaten eines Bild-Frames und gibt ein {@link PictureData}-Objekt zurück.
     *
     * @param data         Roh-Frame-Daten inklusive Kodierungsbyte
     * @param frameId      Die Frame-ID ("APIC" für ID3v2.3/4 oder "PIC" für ID3v2.2)
     * @param majorVersion ID3v2 Hauptversion (2, 3 oder 4)
     * @return das geparste {@link PictureData}-Objekt, oder {@code null} bei Fehlern
     */
    public PictureData parsePictureData(byte[] data, String frameId, int majorVersion) {
        if (data.length < 2) {
            return null;
        }

        try {
            int pos = 0;
            int encoding = data[pos++] & 0xFF;

            if ("APIC".equals(frameId)) {
                int mimeEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, ID3FrameParsingUtils.ISO_8859_1);
                if (mimeEnd == -1) return null;

                String mimeType = new String(data, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
                pos = mimeEnd + 1;
                if (pos >= data.length) return null;

                int pictureType = data[pos++] & 0xFF;
                String pictureTypeStr = ID3FrameParsingUtils.getPictureTypeDescription(pictureType);

                int descEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
                String description = "";
                if (descEnd != -1) {
                    byte[] descData = new byte[descEnd - pos];
                    System.arraycopy(data, pos, descData, 0, descData.length);
                    description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);
                    pos = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
                }

                int pictureDataSize = data.length - pos;
                if (pictureDataSize <= 0) return null;

                byte[] imageData = new byte[pictureDataSize];
                System.arraycopy(data, pos, imageData, 0, pictureDataSize);
                return new PictureData(mimeType, imageData, description, pictureType, pictureTypeStr);

            } else if ("PIC".equals(frameId)) {
                if (data.length < 5) return null;

                String imageFormat = new String(data, pos, 3, StandardCharsets.ISO_8859_1);
                pos += 3;

                int pictureType = data[pos++] & 0xFF;
                String pictureTypeStr = ID3FrameParsingUtils.getPictureTypeDescription(pictureType);

                int descEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
                String description = "";
                if (descEnd != -1) {
                    byte[] descData = new byte[descEnd - pos];
                    System.arraycopy(data, pos, descData, 0, descData.length);
                    description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);
                    pos = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
                }

                int pictureDataSize = data.length - pos;
                if (pictureDataSize <= 0) return null;

                byte[] imageData = new byte[pictureDataSize];
                System.arraycopy(data, pos, imageData, 0, pictureDataSize);
                String mimeType = mapPicFormatToMime(imageFormat);
                return new PictureData(mimeType, imageData, description, pictureType, pictureTypeStr);
            }
        } catch (Exception e) {
            // silent
        }

        return null;
    }

    private static String mapPicFormatToMime(String format) {
        return switch (format.toUpperCase()) {
            case "JPG" -> "image/jpeg";
            case "PNG" -> "image/png";
            case "GIF" -> "image/gif";
            case "BMP" -> "image/bmp";
            default -> "image/" + format.toLowerCase();
        };
    }

    /**
     * Erstellt eine formatierte Informationszeichenkette für ein Bild.
     *
     * <p>Die Ausgabe enthält MIME-Typ/Bildformat, Bildtyp, Datengröße und
     * eine Base64-kodierte Vorschau (maximal 100 Bytes). Wenn die Bilddaten
     * größer als die Vorschau sind, wird "..." angehängt.</p>
     *
     * @param format           Der MIME-Typ (bei APIC) oder das Bildformat (bei PIC)
     * @param pictureTypeStr   Die menschenlesbare Bildtyp-Beschreibung
     * @param description      Die Bildbeschreibung, kann leer sein
     * @param pictureDataSize  Die Größe der Bilddaten in Bytes
     * @param data             Das vollständige Frame-Daten-Array
     * @param pos              Die Startposition der Bilddaten im Array
     * @return Die formatierte Bildinformation im Format [PICTURE:...]
     */
    private String buildPictureInfo(String format, String pictureTypeStr, String description,
                                    int pictureDataSize, byte[] data, int pos) {
        if (pictureDataSize > 0) {
            int previewSize = Math.min(pictureDataSize, 100);
            byte[] previewData = new byte[previewSize];
            System.arraycopy(data, pos, previewData, 0, previewSize);
            String base64Preview = Base64.getEncoder().encodeToString(previewData);

            String result = "[PICTURE:" + format + "," + pictureTypeStr + "," + pictureDataSize + " bytes";
            if (!description.isEmpty()) {
                result += ",desc:" + description;
            }
            result += ",preview:" + base64Preview;
            if (previewSize < pictureDataSize) {
                result += "...";
            }
            result += "]";
            return result;
        }
        return "[PICTURE:" + format + "," + pictureTypeStr + ",no data]";
    }
}