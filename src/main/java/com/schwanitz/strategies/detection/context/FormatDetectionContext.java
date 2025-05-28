package com.schwanitz.strategies.detection.context;

import com.schwanitz.strategies.detection.*;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import com.schwanitz.tagging.FormatPriorityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FormatDetectionContext {

    private static final Logger Log = LoggerFactory.getLogger(FormatDetectionContext.class);

    private final List<TagDetectionStrategy> strategies = new ArrayList<>();

    public FormatDetectionContext() {
        initializeStrategies();
    }

    private void initializeStrategies() {
        strategies.add(new ID3V1DetectionStrategy());
        strategies.add(new ID3V2DetectionStrategy());
        strategies.add(new APEDetectionStrategy());
        strategies.add(new Lyrics3DetectionStrategy());
        strategies.add(new WAVDetectionStrategy());
        strategies.add(new VorbisCommentDetectionStrategy());
        strategies.add(new MP4DetectionStrategy());
        strategies.add(new AIFFDetectionStrategy());
    }

    public List<TagInfo> detectTags(RandomAccessFile file, String filePath, String fileExtension,
                                    byte[] startBuffer, byte[] endBuffer, ScanConfiguration config) throws IOException {

        Log.debug("Start Tag-Detection with Mode: {} for File: {}", config.getMode(), filePath);

        List<TagFormat> formatsToCheck;
        switch (config.getMode()) {
            case FULL_SCAN:
                formatsToCheck = FormatPriorityManager.getFullScanPriority();
                break;
            case COMFORT_SCAN:
                formatsToCheck = FormatPriorityManager.getComfortScanPriority(fileExtension);
                break;
            case CUSTOM_SCAN:
                formatsToCheck = config.getCustomFormats();
                break;
            default:
                throw new IllegalArgumentException("Unknown Scan-Mode: " + config.getMode());
        }

        Set<TagFormat> formatsSet = new HashSet<>(formatsToCheck);
        List<TagInfo> detectedTags = new ArrayList<>();

        for (TagDetectionStrategy strategy : strategies) {
            // Check whether the strategy supports at least one relevant format
            List<TagFormat> supportedFormats = strategy.getSupportedFormats();
            boolean relevant = supportedFormats.stream().anyMatch(formatsSet::contains);
            if (relevant && strategy.canDetect(startBuffer, endBuffer)) {
                try {
                    List<TagInfo> tags = strategy.detectTags(file, filePath, startBuffer, endBuffer);
                    // Only add tags whose format is contained in formatsToCheck
                    tags.stream()
                            .filter(tag -> formatsSet.contains(tag.getFormat()))
                            .forEach(detectedTags::add);
                } catch (Exception e) {
                    Log.warn("Error during detection with strategy {} in File {}: {}",
                            strategy.getClass().getSimpleName(), filePath, e.getMessage());
                }
            }
        }

        Log.debug("Recognized {} Tags", detectedTags.size());
        return detectedTags;
    }
}