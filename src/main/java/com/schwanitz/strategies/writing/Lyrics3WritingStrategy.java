package com.schwanitz.strategies.writing;

import com.schwanitz.io.*;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.strategies.parsing.factory.TagParsingStrategyFactory;
import com.schwanitz.strategies.writing.context.TagWritingStrategy;
import com.schwanitz.tagging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

/**
 * Schreib-Strategie für Lyrics3 Tags (Lyrics3v1 und Lyrics3v2).
 * <p>
 * Lyrics3 Tags befinden sich am Dateiende, vor dem optionalem ID3v1 Tag.
 * </p>
 */
public class Lyrics3WritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(Lyrics3WritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final String LYRICS3_V1_END = "LYRICSEND";
    private static final String LYRICS3_V2_END = "LYRICS200";

    public Lyrics3WritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("Lyrics3", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.LYRICS3V1, TagFormat.LYRICS3V2);
    }

    @Override
    public boolean supportsInPlaceWrite(TagFormat format) {
        return false;
    }

    @Override
    public WriteResult writeTag(TagFormat format, Metadata metadata,
                                 SeekableDataSource source, TagInfo existingTag,
                                 WriteConfiguration config) throws IOException {
        validateInput(metadata, source, format);
        Objects.requireNonNull(config, "config must not be null");

        byte[] tagData = serializeLyrics3(metadata, format);
        long oldSize = existingTag != null ? existingTag.getSize() : 0;

        try (ByteArraySink output = new ByteArraySink(4096)) {
            long sourceLength = source.length();
            if (existingTag != null) {
                if (existingTag.getOffset() > 0) {
                    copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
                }
                output.write(existingTag.getOffset(), tagData);
                long afterTag = existingTag.getOffset() + existingTag.getSize();
                if (afterTag < sourceLength) {
                    copyAudioData(source, output, afterTag,
                            existingTag.getOffset() + tagData.length, COPY_BUFFER_SIZE);
                }
            } else {
                copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
            }

            Files.write(Path.of(source.name()), output.toByteArray());

            return WriteResult.success(format, oldSize, tagData.length, "Lyrics3 Tag geschrieben");
        }
    }

    /**
     * Serialisiert Metadaten als Lyrics3 Tag.
     */
    private byte[] serializeLyrics3(Metadata metadata, TagFormat format) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        if (format == TagFormat.LYRICS3V2) {
            // Lyrics3v2: Feld-basiert mit Längenangaben
            for (MetadataField<?> field : metadata.getFields()) {
                String key = field.getKey();
                String value = field.getValue().toString();
                byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
                byte[] keyBytes = key.getBytes(StandardCharsets.US_ASCII);
                // Feld: ID(3B) + Länge(6B ASCII) + Daten
                String lenStr = String.format("%06d", valueBytes.length);
                buffer.write(keyBytes, 0, Math.min(keyBytes.length, 3));
                buffer.write(lenStr.getBytes(StandardCharsets.US_ASCII), 0, 6);
                buffer.write(valueBytes, 0, valueBytes.length);
            }
            // Footer
            byte[] footer = LYRICS3_V2_END.getBytes(StandardCharsets.US_ASCII);
            buffer.write(footer, 0, footer.length);
        } else {
            // Lyrics3v1: Einfacher Text
            String lyrics = getLyricsText(metadata);
            byte[] lyricsBytes = lyrics.getBytes(StandardCharsets.UTF_8);
            buffer.write(lyricsBytes, 0, lyricsBytes.length);
            byte[] footer = LYRICS3_V1_END.getBytes(StandardCharsets.US_ASCII);
            buffer.write(footer, 0, footer.length);
        }

        return buffer.toByteArray();
    }

    private String getLyricsText(Metadata metadata) {
        for (MetadataField<?> field : metadata.getFields()) {
            if ("LYRICS".equals(field.getKey()) || "USLT".equals(field.getKey())) {
                return field.getValue().toString();
            }
        }
        // Alle Felder als Text
        StringBuilder sb = new StringBuilder();
        for (MetadataField<?> field : metadata.getFields()) {
            sb.append(field.getKey()).append(": ").append(field.getValue()).append("\n");
        }
        return sb.toString();
    }
}
