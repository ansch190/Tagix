package com.schwanitz.strategies.parsing.id3;

import java.nio.charset.StandardCharsets;

/**
 * Parser für General Encapsulated Object Frames (GEOB / GEO).
 */
public class GeneralObjectFrameParser implements ID3FrameParser {

    @Override
    public String parse(byte[] data, String frameId, int majorVersion) {
        if (data.length < 2) {
            return "";
        }
        int encoding = data[0] & 0xFF;
        int pos = 1;

        int mimeEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, ID3FrameParsingUtils.ISO_8859_1);
        if (mimeEnd == -1) {
            return "[OBJECT: Invalid MIME type]";
        }

        String mimeType = new String(data, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
        pos = mimeEnd + 1;

        int filenameEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
        String filename = "";
        if (filenameEnd != -1) {
            byte[] filenameData = new byte[filenameEnd - pos];
            System.arraycopy(data, pos, filenameData, 0, filenameData.length);
            filename = ID3FrameParsingUtils.decodeText(filenameData, encoding, majorVersion);
            pos = filenameEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
        }

        int descEnd = ID3FrameParsingUtils.findNullTerminator(data, pos, encoding);
        String description = "";
        if (descEnd != -1) {
            byte[] descData = new byte[descEnd - pos];
            System.arraycopy(data, pos, descData, 0, descData.length);
            description = ID3FrameParsingUtils.decodeText(descData, encoding, majorVersion);
            pos = descEnd + ID3FrameParsingUtils.getNullTerminatorSize(encoding);
        }

        int objectDataSize = data.length - pos;
        String result = "[OBJECT:" + mimeType + "," + objectDataSize + " bytes";
        if (!filename.isEmpty()) {
            result += ",file:" + filename;
        }
        if (!description.isEmpty()) {
            result += ",desc:" + description;
        }
        result += "]";
        return result;
    }
}
