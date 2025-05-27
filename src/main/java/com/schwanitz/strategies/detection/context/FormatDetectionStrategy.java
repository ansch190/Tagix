package com.schwanitz.strategies.detection.context;

import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

public interface FormatDetectionStrategy {

    boolean canDetect(byte[] startBuffer, byte[] endBuffer);
    List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException;
}