package com.schwanitz.metadata;

import com.schwanitz.interfaces.FieldHandler;

public class PictureFieldHandler implements FieldHandler<PictureData> {

    private final String key;

    public PictureFieldHandler(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public PictureData readData(byte[] data) {
        String mimeType = detectMimeType(data);
        return new PictureData(mimeType, data, "", -1, "Unknown");
    }

    @Override
    public byte[] writeData(PictureData value) {
        return value.getData();
    }

    public static String detectMimeType(byte[] data) {
        if (data == null || data.length < 4) return "application/octet-stream";
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) return "image/jpeg";
        if (data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') return "image/png";
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') return "image/gif";
        if (data[0] == 'B' && data[1] == 'M') return "image/bmp";
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') return "image/webp";
        if (data.length >= 4 && data[0] == 0x00 && data[1] == 0x00 && data[2] == 0x01 && data[3] == 0x00) return "image/x-icon";
        return "application/octet-stream";
    }
}
