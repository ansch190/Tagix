package com.schwanitz.metadata;

import java.util.Arrays;
import java.util.Objects;

public class PictureData {

    private final String mimeType;
    private final byte[] data;
    private final String description;
    private final int pictureType;
    private final String pictureTypeName;
    private final int width;
    private final int height;
    private final int colorDepth;

    public PictureData(String mimeType, byte[] data, String description,
                       int pictureType, String pictureTypeName) {
        this(mimeType, data, description, pictureType, pictureTypeName, -1, -1, -1);
    }

    public PictureData(String mimeType, byte[] data, String description,
                       int pictureType, String pictureTypeName,
                       int width, int height, int colorDepth) {
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        this.data = Objects.requireNonNull(data, "data");
        this.description = description != null ? description : "";
        this.pictureType = pictureType;
        this.pictureTypeName = pictureTypeName != null ? pictureTypeName : "Unknown";
        this.width = width;
        this.height = height;
        this.colorDepth = colorDepth;
    }

    public String getMimeType() { return mimeType; }
    public byte[] getData() { return data; }
    public String getDescription() { return description; }
    public int getPictureType() { return pictureType; }
    public String getPictureTypeName() { return pictureTypeName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getColorDepth() { return colorDepth; }

    public String getImageFormat() {
        int slash = mimeType.indexOf('/');
        return slash >= 0 ? mimeType.substring(slash + 1).toUpperCase() : mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PictureData that)) return false;
        return pictureType == that.pictureType
            && width == that.width
            && height == that.height
            && colorDepth == that.colorDepth
            && mimeType.equals(that.mimeType)
            && Arrays.equals(data, that.data)
            && description.equals(that.description)
            && pictureTypeName.equals(that.pictureTypeName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mimeType, description, pictureType, pictureTypeName, width, height, colorDepth);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "[Picture:" + getImageFormat() + "," + pictureTypeName + ","
            + data.length + " bytes" + (width > 0 && height > 0 ? "," + width + "x" + height : "")
            + "]";
    }
}
