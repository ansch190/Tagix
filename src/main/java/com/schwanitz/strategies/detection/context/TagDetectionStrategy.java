package com.schwanitz.strategies.detection.context;

import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

public abstract class TagDetectionStrategy {

    protected final Logger Log = LoggerFactory.getLogger(getClass());

    public abstract TagFormat getTagFormat();

    public abstract boolean canDetect(byte[] startBuffer, byte[] endBuffer);

    public abstract List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException;
}