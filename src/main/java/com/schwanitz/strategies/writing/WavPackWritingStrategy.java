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
 * Schreib-Strategie für WavPack Native Tags.
 * <p>
 * Schreibt Metadaten als RIFF Header Sub-Blöcke in WavPack-Dateien.
 * </p>
 */
public class WavPackWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(WavPackWritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    public WavPackWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("WavPack", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.WAVPACK_NATIVE);
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

        byte[] tagData = serializeWavPackTags(metadata);
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

            return WriteResult.success(format, oldSize, tagData.length, "WavPack Tags geschrieben");
        }
    }

    /**
     * Serialisiert Metadaten als WavPack RIFF Header.
     */
    private byte[] serializeWavPackTags(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (MetadataField<?> field : metadata.getFields()) {
            String key = field.getKey();
            byte[] value = field.getValue().toString().getBytes(StandardCharsets.UTF_8);
            buffer.write(key.getBytes(StandardCharsets.US_ASCII), 0, key.length());
            buffer.write('=');
            buffer.write(value, 0, value.length);
            buffer.write('\0');
        }
        return buffer.toByteArray();
    }
}
