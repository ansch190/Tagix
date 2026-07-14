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
 * Schreib-Strategie für MP4/iTunes Tags.
 * <p>
 * Schreibt ilst-Items in die Atom-Struktur moov→udta→meta→ilst.
 * Bei Größenänderung wird das moov-Atom komplett neu geschrieben.
 * </p>
 */
public class MP4WritingStrategy extends AbstractTagWritingStrategy implements TagWritingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MP4WritingStrategy.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    public MP4WritingStrategy(TagParsingStrategyFactory parsingFactory) {
        super("MP4", parsingFactory);
    }

    @Override
    public List<TagFormat> getSupportedWriteFormats() {
        return List.of(TagFormat.MP4);
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

        byte[] ilstData = serializeIlst(metadata);
        long oldSize = existingTag != null ? existingTag.getSize() : 0;

        try (ByteArraySink output = new ByteArraySink(4096)) {
            long sourceLength = source.length();

            if (existingTag != null) {
                // Kopiere alles VOR dem Tag
                if (existingTag.getOffset() > 0) {
                    copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
                }
                // Neues ilst schreiben
                output.write(existingTag.getOffset(), ilstData);
                // Rest kopieren
                long afterTag = existingTag.getOffset() + existingTag.getSize();
                if (afterTag < sourceLength) {
                    copyAudioData(source, output, afterTag,
                            existingTag.getOffset() + ilstData.length, COPY_BUFFER_SIZE);
                }
            } else {
                // Kein bestehendes Tag: Gesamte Datei kopieren
                copyAudioData(source, output, 0, 0, COPY_BUFFER_SIZE);
            }

            Files.write(Path.of(source.name()), output.toByteArray());

            return WriteResult.success(format, oldSize, ilstData.length, "MP4 Tags geschrieben");
        }
    }

    /**
     * Serialisiert Metadaten als ilst-Atom (iTunes Metadata).
     */
    private byte[] serializeIlst(Metadata metadata) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        for (MetadataField<?> field : metadata.getFields()) {
            byte[] item = serializeIlstItem(field);
            buffer.write(item, 0, item.length);
        }

        byte[] itemsData = buffer.toByteArray();
        // ilst-Atom Header (8B: size + "ilst") + Items
        int totalSize = 8 + itemsData.length;
        byte[] result = new byte[totalSize];
        writeBigEndianInt32(result, 0, totalSize);
        System.arraycopy("ilst".getBytes(StandardCharsets.US_ASCII), 0, result, 4, 4);
        System.arraycopy(itemsData, 0, result, 8, itemsData.length);
        return result;
    }

    private byte[] serializeIlstItem(MetadataField<?> field) {
        String key = field.getKey();
        Object valueObj = field.getValue();
        String value = valueObj != null ? valueObj.toString() : "";
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        // Item-Atom: size(4B) + key(4B) + data-Atom(12B + value)
        int dataSize = 16 + valueBytes.length; // data-Atom: size(4) + "data"(4) + type(4) + BE(4)
        int itemSize = 8 + dataSize; // item: size(8) + data-Atom

        byte[] result = new byte[itemSize];
        writeBigEndianInt32(result, 0, itemSize);
        System.arraycopy(key.getBytes(StandardCharsets.US_ASCII), 0, result, 4, 4);

        // data-Atom
        writeBigEndianInt32(result, 8, dataSize);
        System.arraycopy("data".getBytes(StandardCharsets.US_ASCII), 0, result, 12, 4);
        writeBigEndianInt32(result, 16, 1); // Type: UTF-8
        writeBigEndianInt32(result, 20, 0); // Locale: 0
        System.arraycopy(valueBytes, 0, result, 24, valueBytes.length);

        return result;
    }

    private void writeBigEndianInt32(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }
}
