package com.schwanitz.strategies.detection.context;

import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * Abstract base class for tag detection strategies
 * <p>
 * Each strategy implements detection for one or more specific tag formats.
 * The detection process uses a two-phase approach: quick signature detection
 * followed by detailed parsing if the format is detected.
 */
public abstract class TagDetectionStrategy {

    protected final Logger Log = LoggerFactory.getLogger(getClass());

    /**
     * Returns the tag formats supported by this detection strategy
     */
    public abstract List<TagFormat> getSupportedFormats();

    /**
     * Fast signature-based detection using file buffers
     */
    public abstract boolean canDetect(byte[] startBuffer, byte[] endBuffer);

    /**
     * Detailed tag parsing to extract exact locations and sizes
     */
    public abstract List<TagInfo> detectTags(RandomAccessFile file, String filePath, byte[] startBuffer, byte[] endBuffer) throws IOException;
}