package com.schwanitz.tagging;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagInfo tagInfo = (TagInfo) o;
        return offset == tagInfo.offset && size == tagInfo.size && format == tagInfo.format;
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, offset, size);
    }

    @Override
    public String toString() {
        return "TagInfo{format=" + format.getFormatName() +
                ", offset=" + offset +
                ", size=" + size + '}';
    }
}