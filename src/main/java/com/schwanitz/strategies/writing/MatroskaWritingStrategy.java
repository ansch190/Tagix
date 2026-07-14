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
 * Schreib-Strategie für Matroska Tags (MKV/MKA/WebM).
 * <p>
 * Schreibt Tags als EBML-Elemente (SimpleTag → TagName + TagString).
 * </p>
 */
public class MatroskaWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MatroskaWritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    public MatroskaWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("Matroska", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.MATROSKA_TAGS, TagFormat.WEBM_TAGS);
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

        byte[] tagData = serializeMatroskaTags(metadata);
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

            return WriteResult.success(format, oldSize, tagData.length, "Matroska Tags geschrieben");
        }
    }

    /**
     * Serialisiert Metadaten als Matroska Tags Element.
     */
    private byte[] serializeMatroskaTags(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        for (MetadataField<?> field : metadata.getFields()) {
            byte[] simpleTag = serializeSimpleTag(field.getKey(),
                    field.getValue() != null ? field.getValue().toString() : "");
            // SimpleTag Element (0x3373)
            writeEBMLElement(buffer, 0x3373, simpleTag);
        }

        byte[] tagsContent = buffer.toByteArray();
        // Tags Element (0x1254C367)
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeEBMLElement(result, 0x1254C367, tagsContent);

        return result.toByteArray();
    }

    private byte[] serializeSimpleTag(String name, String value) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // TagName Element (0x45A3)
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        writeEBMLElement(buffer, 0x45A3, nameBytes);
        // TagString Element (0x4487)
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        writeEBMLElement(buffer, 0x4487, valueBytes);
        return buffer.toByteArray();
    }

    /**
     * Schreibt ein EBML-Element mit VLI-kodierter Größe.
     */
    private void writeEBMLElement(ByteArrayOutputStream buffer, int elementId, byte[] data) {
        // Element-ID (Big-Endian, variable Größe)
        writeEBMLInt(buffer, elementId);
        // Data-Size (VLI)
        writeEBMLInt(buffer, data.length);
        // Data
        buffer.write(data, 0, data.length);
    }

    private void writeEBMLInt(ByteArrayOutputStream buffer, int value) {
        if (value < 0x80) {
            buffer.write(value);
        } else if (value < 0x4000) {
            buffer.write((value >> 8) | 0x80);
            buffer.write(value & 0xFF);
        } else if (value < 0x200000) {
            buffer.write((value >> 16) | 0xC0);
            buffer.write((value >> 8) & 0xFF);
            buffer.write(value & 0xFF);
        } else {
            buffer.write((value >> 24) | 0xE0);
            buffer.write((value >> 16) & 0xFF);
            buffer.write((value >> 8) & 0xFF);
            buffer.write(value & 0xFF);
        }
    }
}
