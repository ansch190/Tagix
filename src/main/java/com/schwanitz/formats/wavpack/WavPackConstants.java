package com.schwanitz.formats.wavpack;

public final class WavPackConstants {

    private WavPackConstants() {}

    public static final byte[] WAVPACK_SIGNATURE = {'w', 'v', 'p', 'k'};
    public static final int WAVPACK_HEADER_SIZE = 32;

    public static final int SUBBLOCK_LARGE_FLAG = 0x80;
    public static final int SUBBLOCK_ID_MASK = 0x7F;
}
