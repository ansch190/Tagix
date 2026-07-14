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
 * Schreib-Strategie für APE Tags (APEv1 und APEv2).
 * <p>
 * APE Tags befinden sich am Dateiende. Das Format besteht aus einem
 * 32-Byte Header/ Footer gefolgt von Schlüssel-Wert-Paaren.
 * </p>
 */
public class APEWritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(APEWritingStrategy.class);

    private static final String APE_PREAMBLE = "APETAGEX";
    private static final int APE_HEADER_SIZE = 32;
    private static final int APE_VERSION_2 = 2000;
    private static final int APE_TAG_FLAG_HAS_HEADER = 0x80000000;
    private static final int APE_TAG_FLAG_HAS_FOOTER = 0x40000000;
    private static final int APE_TAG_FLAG_IS_HEADER = 0x20000000;
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    /**
     * Erzeugt eine neue APE-Schreib-Strategie.
     *
     * @param parsingFactory die Factory zum Lesen bestehender Tags
     */
    public APEWritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("APE", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.APEV1, TagFormat.APEV2);
    }

    @Override
    public boolean supportsInPlaceWrite(TagFormat format) {
        return true; // APE kann In-Place schreiben wenn Größe gleich
    }

    @Override
    public WriteResult writeTag(TagFormat format, Metadata metadata,
                                 SeekableDataSource source, TagInfo existingTag,
                                 WriteConfiguration config) throws IOException {
        validateInput(metadata, source, format);
        Objects.requireNonNull(config, "config must not be null");

        byte[] tagBytes = serializeAPEv2(metadata);
        long oldSize = existingTag != null ? existingTag.getSize() : 0;

        try (ByteArraySink output = new ByteArraySink(4096)) {
            long sourceLength = source.length();
            long audioEnd;

            if (existingTag != null) {
                audioEnd = existingTag.getOffset();
            } else {
                audioEnd = sourceLength;
            }

            // Audiodaten kopieren
            long sinkOffset = 0;
            if (audioEnd > 0) {
                sinkOffset = copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
            }

            // APE Tag anhängen
            output.write(sinkOffset, tagBytes);

            Files.write(Path.of(source.name()), output.toByteArray());

            return WriteResult.success(format, oldSize, tagBytes.length,
                    "APEv2 geschrieben (" + tagBytes.length + " Bytes)");
        }
    }

    /**
     * Serialisiert Metadaten als APEv2 Tag (Header + Items + Footer).
     */
    private byte[] serializeAPEv2(Metadata metadata) {
        byte[] itemsData = serializeAPEItems(metadata);
        int totalTagSize = APE_HEADER_SIZE + itemsData.length + APE_HEADER_SIZE; // Header + Items + Footer

        byte[] result = new byte[totalTagSize];

        // Header
        writeAPEHeader(result, 0, APE_VERSION_2, itemsData.length, metadata.getFields().size(),
                APE_TAG_FLAG_HAS_FOOTER | APE_TAG_FLAG_IS_HEADER);

        // Items
        System.arraycopy(itemsData, 0, result, APE_HEADER_SIZE, itemsData.length);

        // Footer (identisch mit Header, aber IS_HEADER Flag nicht gesetzt)
        writeAPEHeader(result, APE_HEADER_SIZE + itemsData.length,
                APE_VERSION_2, itemsData.length, metadata.getFields().size(),
                APE_TAG_FLAG_HAS_HEADER);

        return result;
    }

    /**
     * Schreibt einen APE Header/Footer.
     */
    private void writeAPEHeader(byte[] data, int offset, int version, int tagSize,
                                 int itemCount, int flags) {
        // Preamble: "APETAGEX"
        byte[] preamble = APE_PREAMBLE.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(preamble, 0, data, offset, 8);

        // Version (LE32)
        writeLittleEndianInt32(data, offset + 8, version);

        // Tag Size (LE32) - Größe ohne Header/Footer
        writeLittleEndianInt32(data, offset + 12, tagSize);

        // Item Count (LE32)
        writeLittleEndianInt32(data, offset + 16, itemCount);

        // Flags (LE32)
        writeLittleEndianInt32(data, offset + 20, flags);
    }

    /**
     * Serialisiert alle Metadaten-Felder als APE Items.
     */
    private byte[] serializeAPEItems(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        for (MetadataField<?> field : metadata.getFields()) {
            byte[] item = serializeAPEItem(field);
            buffer.write(item, 0, item.length);
        }

        return buffer.toByteArray();
    }

    /**
     * Serialisiert ein einzelnes APE Item (ValueSize + Flags + Key\0 + Value).
     */
    private byte[] serializeAPEItem(MetadataField<?> field) {
        String key = field.getKey();
        Object valueObj = field.getValue();
        String value = valueObj != null ? valueObj.toString() : "";
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Value Size (LE32)
        writeLittleEndianInt32Stream(buffer, valueBytes.length);

        // Flags (LE32) - UTF-8 Text (0x00)
        writeLittleEndianInt32Stream(buffer, 0x00);

        // Key (null-terminiert)
        byte[] keyBytes = key.getBytes(StandardCharsets.US_ASCII);
        buffer.write(keyBytes, 0, keyBytes.length);
        buffer.write(0); // Null-Terminator

        // Value
        buffer.write(valueBytes, 0, valueBytes.length);

        return buffer.toByteArray();
    }

    private void writeLittleEndianInt32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private void writeLittleEndianInt32Stream(ByteArrayOutputStream buffer, int value) {
        buffer.write(value & 0xFF);
        buffer.write((value >> 8) & 0xFF);
        buffer.write((value >> 16) & 0xFF);
        buffer.write((value >> 24) & 0xFF);
    }
}
