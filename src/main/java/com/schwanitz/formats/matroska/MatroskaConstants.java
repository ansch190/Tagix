package com.schwanitz.formats.matroska;

public final class MatroskaConstants {

    private MatroskaConstants() {}

    public static final byte[] EBML_HEADER = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    public static final byte[] SEGMENT_ID = {0x18, 0x53, (byte) 0x80, 0x67};
    public static final byte[] TAGS_ID = {0x12, 0x54, (byte) 0xC3, 0x67};

    public static final int EBML_HEADER_ID_SIZE = 4;

    public static final long EBML_DOCTYPE_ID = 0x4282L;
    public static final long EBML_TAGS_ELEMENT_ID = 0x1254C367L;

    public static final int VLI_LEADING_BIT_MASK = 0x80;
}
