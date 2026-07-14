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
 * Schreib-Strategie für AIFF Metadata Tags.
 * <p>
 * Schreibt IFF-Chunks (NAME, AUTH, ANNO, COMT) in AIFF Dateien.
 * </p>
 */
public class AIFFWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AIFFWritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    public AIFFWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("AIFF", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.AIFF_METADATA);
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

        byte[] tagData = serializeAIFFChunks(metadata);
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

            return WriteResult.success(format, oldSize, tagData.length, "AIFF Tags geschrieben");
        }
    }

    /**
     * Serialisiert Metadaten als IFF-Chunks.
     */
    private byte[] serializeAIFFChunks(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (MetadataField<?> field : metadata.getFields()) {
            byte[] value = field.getValue().toString().getBytes(StandardCharsets.US_ASCII);
            String chunkId = padRight(field.getKey(), 4);
            byte[] idBytes = chunkId.getBytes(StandardCharsets.US_ASCII);
            int paddedSize = (value.length + 1) & ~1;
            buffer.write(idBytes, 0, 4);
            writeBigEndianInt32Stream(buffer, value.length);
            buffer.write(value, 0, value.length);
            if (paddedSize > value.length) {
                buffer.write(0);
            }
        }
        return buffer.toByteArray();
    }

    private String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    private void writeBigEndianInt32Stream(ByteArrayOutputStream buffer, int value) {
        buffer.write((value >> 24) & 0xFF);
        buffer.write((value >> 16) & 0xFF);
        buffer.write((value >> 8) & 0xFF);
        buffer.write(value & 0xFF);
    }
}
