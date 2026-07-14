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
 * Schreib-Strategie für RIFF INFO und BWF Tags (WAV-Dateien).
 * <p>
 * Schreibt LIST/INFO Chunks und BWF bext Chunks in RIFF/WAVE Dateien.
 * </p>
 */
public class RIFFWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(RIFFWritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    public RIFFWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("RIFF", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.RIFF_INFO, TagFormat.BWF_V0, TagFormat.BWF_V1, TagFormat.BWF_V2);
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

        byte[] tagData = switch (format) {
            case RIFF_INFO -> serializeRIFFInfo(metadata);
            case BWF_V0, BWF_V1, BWF_V2 -> serializeBWF(metadata);
            default -> throw new IllegalArgumentException("Nicht unterstütztes RIFF-Format: " + format);
        };

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

            return WriteResult.success(format, oldSize, tagData.length, "RIFF/BWF Tag geschrieben");
        }
    }

    /**
     * Serialisiert Metadaten als LIST/INFO Chunk.
     */
    private byte[] serializeRIFFInfo(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (MetadataField<?> field : metadata.getFields()) {
            String id = padRight(field.getKey(), 4);
            byte[] value = field.getValue().toString().getBytes(StandardCharsets.US_ASCII);
            int paddedSize = (value.length + 1) & ~1; // Auf gerade Größe aufrunden
            byte[] paddedValue = new byte[paddedSize];
            System.arraycopy(value, 0, paddedValue, 0, value.length);
            byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
            buffer.write(idBytes, 0, 4);
            writeBigEndianInt32Stream(buffer, value.length);
            buffer.write(paddedValue, 0, paddedSize);
        }
        byte[] itemsData = buffer.toByteArray();
        byte[] listHeader = new byte[12];
        writeBigEndianInt32(listHeader, 0, 4 + itemsData.length); // "LIST" + size
        System.arraycopy("LIST".getBytes(), 0, listHeader, 4, 4);
        System.arraycopy("INFO".getBytes(), 0, listHeader, 8, 4);

        byte[] result = new byte[12 + itemsData.length];
        System.arraycopy(listHeader, 0, result, 0, 12);
        System.arraycopy(itemsData, 0, result, 12, itemsData.length);
        return result;
    }

    /**
     * Serialisiert Metadaten als BWF bext-Chunk.
     */
    private byte[] serializeBWF(Metadata metadata) {
        // bext-Chunk: 602 Bytes fixed + extension data
        byte[] bext = new byte[602];
        writeFixedField(bext, 0, getFieldString(metadata, "Description"), 256);
        writeFixedField(bext, 256, getFieldString(metadata, "Originator"), 32);
        writeFixedField(bext, 288, getFieldString(metadata, "OriginatorRef"), 32);
        writeFixedField(bext, 320, getFieldString(metadata, "OriginationDate"), 10);
        writeFixedField(bext, 330, getFieldString(metadata, "OriginationTime"), 8);
        // TimeRef (8B) at 338
        writeFixedField(bext, 346, "1", 2); // Version
        // UMID (64B) at 348
        // Loudness etc. at 412

        byte[] header = new byte[8];
        System.arraycopy("bext".getBytes(), 0, header, 0, 4);
        writeBigEndianInt32(header, 4, bext.length);

        byte[] result = new byte[8 + bext.length];
        System.arraycopy(header, 0, result, 0, 8);
        System.arraycopy(bext, 0, result, 8, bext.length);
        return result;
    }

    private String getFieldString(Metadata metadata, String key) {
        for (MetadataField<?> field : metadata.getFields()) {
            if (field.getKey().equals(key)) {
                return field.getValue() != null ? field.getValue().toString() : "";
            }
        }
        return "";
    }

    private void writeFixedField(byte[] data, int offset, String value, int maxLength) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(bytes.length, maxLength);
        System.arraycopy(bytes, 0, data, offset, len);
    }

    private String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    private void writeBigEndianInt32(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private void writeBigEndianInt32Stream(ByteArrayOutputStream buffer, int value) {
        buffer.write((value >> 24) & 0xFF);
        buffer.write((value >> 16) & 0xFF);
        buffer.write((value >> 8) & 0xFF);
        buffer.write(value & 0xFF);
    }
}
