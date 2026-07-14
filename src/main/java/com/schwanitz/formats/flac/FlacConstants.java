package com.schwanitz.formats.flac;

public final class FlacConstants {

    private FlacConstants() {}

    public static final int FLAC_SIGNATURE_LENGTH = 4;
    public static final int FLAC_BLOCK_HEADER_SIZE = 4;
    public static final int FLAC_APPLICATION_ID_SIZE = 4;
    public static final int FLAC_APPLICATION_BLOCK_TYPE = 2;
    public static final int FLAC_VORBIS_COMMENT_BLOCK_TYPE = 4;

    public static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    public static final int FLAC_BLOCK_TYPE_MASK = 0x7F;

    public static final int MAX_BLOCKS = 1000;
}
