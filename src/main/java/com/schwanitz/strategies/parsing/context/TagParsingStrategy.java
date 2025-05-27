package com.schwanitz.strategies.parsing.context;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;

public interface TagParsingStrategy {

    boolean canHandle(TagFormat format);
    Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException;
}