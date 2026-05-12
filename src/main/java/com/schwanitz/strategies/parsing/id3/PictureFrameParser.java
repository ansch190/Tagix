package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Parser für Attached Picture Frames (APIC / PIC).
 */
public class PictureFrameParser implements ID3FrameParser {

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
