package com.schwanitz.strategies.detection.context;

import com.schwanitz.strategies.detection.*;
import com.schwanitz.tagging.TagInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class FormatDetectionContext {
    private final List<FormatDetectionStrategy> strategies = new ArrayList<>();

    public FormatDetectionContext() {
        // Alle verfügbaren Strategien registrieren
        strategies.add(new MP3DetectionStrategy());
        strategies.add(new WAVDetectionStrategy());
        strategies.add(new OggFlacDetectionStrategy());
        strategies.add(new MP4DetectionStrategy());
        strategies.add(new AIFFDetectionStrategy());
    }

    public void addStrategy(FormatDetectionStrategy strategy) {
        strategies.add(strategy);
    }

    public List<TagInfo> detectAllFormats(RandomAccessFile file, String filePath, String fileExtension,
                                          byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> allDetectedTags = new ArrayList<>();

        for (FormatDetectionStrategy strategy : strategies) {
            if (strategy.canDetect(fileExtension, startBuffer, endBuffer)) {
                List<TagInfo> detectedTags = strategy.detectTags(file, filePath, startBuffer, endBuffer);
                allDetectedTags.addAll(detectedTags);
            }
        }

        return allDetectedTags;
    }

    public List<TagInfo> detectFullSearch(RandomAccessFile file, String filePath, String fileExtension,
                                          byte[] startBuffer, byte[] endBuffer) throws IOException {
        List<TagInfo> allDetectedTags = new ArrayList<>();

        // Bei fullSearch alle Strategien anwenden, unabhängig von canDetect
        for (FormatDetectionStrategy strategy : strategies) {
            try {
                List<TagInfo> detectedTags = strategy.detectTags(file, filePath, startBuffer, endBuffer);
                // Duplikate vermeiden
                for (TagInfo newTag : detectedTags) {
                    boolean isDuplicate = allDetectedTags.stream()
                            .anyMatch(existingTag -> existingTag.getFormat() == newTag.getFormat());
                    if (!isDuplicate) {
                        allDetectedTags.add(newTag);
                    }
                }
            } catch (Exception e) {
                // Fehler in einer Strategie sollten nicht die anderen blockieren
                System.err.println("Fehler in Strategie " + strategy.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        return allDetectedTags;
    }
}