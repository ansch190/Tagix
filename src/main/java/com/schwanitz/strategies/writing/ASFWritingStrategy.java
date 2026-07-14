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
 * Schreib-Strategie für ASF Tags (WMA/WMV Dateien).
 * <p>
 * Schreibt Content Description und Extended Content Description Objekte.
 * </p>
 */
public class ASFWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ASFWritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    // ASF GUIDs (16 Bytes)
    private static final byte[] ASF_HEADER_GUID = {
            (byte) 0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11,
            (byte) 0xA6, (byte) 0xD9, 0x00, (byte) 0xAA, 0x00, (byte) 0x62, (byte) 0xCE, 0x6C
    };

    public ASFWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("ASF", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.ASF_CONTENT_DESC, TagFormat.ASF_EXT_CONTENT_DESC);
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

        byte[] tagData = serializeASFContent(metadata);
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

            return WriteResult.success(format, oldSize, tagData.length, "ASF Tags geschrieben");
        }
    }

    /**
     * Serialisiert Metadaten als ASF Content Description Object.
     */
    private byte[] serializeASFContent(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // Vereinfachte Serialisierung: Title, Author, Copyright, Description
        writeASFUnicodeString(buffer, getFieldString(metadata, "Title"));
        writeASFUnicodeString(buffer, getFieldString(metadata, "Author"));
        writeASFUnicodeString(buffer, ""); // Rating
        writeASFUnicodeString(buffer, getFieldString(metadata, "Copyright"));
        writeASFUnicodeString(buffer, getFieldString(metadata, "Description"));

        byte[] data = buffer.toByteArray();
        byte[] header = new byte[24]; // GUID(16) + Size(8)
        System.arraycopy(ASF_HEADER_GUID, 0, header, 0, 16);
        writeLittleEndianInt64(header, 16, 24 + data.length);

        byte[] result = new byte[24 + data.length];
        System.arraycopy(header, 0, result, 0, 24);
        System.arraycopy(data, 0, result, 24, data.length);
        return result;
    }

    private void writeASFUnicodeString(ByteArrayOutputStream buffer, String value) {
        byte[] utf16 = value.getBytes(StandardCharsets.UTF_16LE);
        writeLittleEndianInt16Stream(buffer, (short) (utf16.length + 2)); // Länge inkl. Null
        buffer.write(utf16, 0, utf16.length);
        buffer.write(0); // Null-Terminator (2 Bytes für UTF-16)
        buffer.write(0);
    }

    private String getFieldString(Metadata metadata, String key) {
        for (MetadataField<?> field : metadata.getFields()) {
            if (field.getKey().equals(key)) {
                return field.getValue() != null ? field.getValue().toString() : "";
            }
        }
        return "";
    }

    private void writeLittleEndianInt64(byte[] data, int offset, long value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
        data[offset + 4] = (byte) ((value >> 32) & 0xFF);
        data[offset + 5] = (byte) ((value >> 40) & 0xFF);
        data[offset + 6] = (byte) ((value >> 48) & 0xFF);
        data[offset + 7] = (byte) ((value >> 56) & 0xFF);
    }

    private void writeLittleEndianInt16Stream(ByteArrayOutputStream buffer, short value) {
        buffer.write(value & 0xFF);
        buffer.write((value >> 8) & 0xFF);
    }
}
