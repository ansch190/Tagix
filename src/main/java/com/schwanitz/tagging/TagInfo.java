package com.schwanitz.tagging;

public class TagInfo {

    private final TagFormat format;
    private final long offset;
    private final long size;

    public TagInfo(TagFormat format, long offset, long size) {
        this.format = format;
        this.offset = offset;
        this.size = size;
    }

    public TagFormat getFormat() {
        return format;
    }

    public long getOffset() {
        return offset;
    }

    public long getSize() {
        return size;
    }

}