package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DSFParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DSFParsingStrategy.class);

    private static final byte[] DSF_SIGNATURE = {'D', 'S', 'D', ' '};
    private static final byte[] DSF_ID3_CHUNK = {'I', 'D', '3', ' '};
    private static final int DSF_METADATA_POINTER_OFFSET = 20;
    private static final int DSF_ID3_CHUNK_HEADER_SIZE = 12;

    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();

    public DSFParsingStrategy() {
        handlers.put("DSF_ID3", new TextFieldHandler("DSF_ID3"));
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.DSF_METADATA;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        DSFMetadata metadata = new DSFMetadata();

        file.seek(offset);

        byte[] chunkId = new byte[4];
        file.read(chunkId);

        if (!Arrays.equals(chunkId, DSF_ID3_CHUNK)) {
            LOG.debug("DSF metadata chunk is not an ID3 chunk at offset {}", offset);
            return metadata;
        }

        long id3ChunkSize = readLittleEndianLong(file);
        long id3DataOffset = offset + DSF_ID3_CHUNK_HEADER_SIZE;
        long id3DataSize = id3ChunkSize - DSF_ID3_CHUNK_HEADER_SIZE;

        if (id3DataSize <= 0 || id3DataOffset + id3DataSize > file.length()) {
            LOG.warn("Invalid DSF ID3 chunk size: {} at offset {}", id3ChunkSize, offset);
            return metadata;
        }

        byte[] id3Header = new byte[10];
        file.seek(id3DataOffset);
        int bytesRead = file.read(id3Header);
        if (bytesRead < 10 || !new String(id3Header, 0, 3, StandardCharsets.US_ASCII).equals("ID3")) {
            LOG.debug("DSF ID3 chunk does not contain valid ID3v2 header");
            return metadata;
        }

        byte version = id3Header[3];
        String versionStr = switch (version) {
            case 2 -> "ID3v2.2";
            case 3 -> "ID3v2.3";
            case 4 -> "ID3v2.4";
            default -> "ID3v2." + version;
        };

        addField(metadata, "DSF_ID3", "Contains " + versionStr + " data (" + id3DataSize + " bytes)");
        metadata.setId3DataOffset(id3DataOffset);
        metadata.setId3DataSize(id3DataSize);

        return metadata;
    }

    private long readLittleEndianLong(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);
        return ((long)(bytes[0] & 0xFF)) |
                ((long)(bytes[1] & 0xFF) << 8) |
                ((long)(bytes[2] & 0xFF) << 16) |
                ((long)(bytes[3] & 0xFF) << 24) |
                ((long)(bytes[4] & 0xFF) << 32) |
                ((long)(bytes[5] & 0xFF) << 40) |
                ((long)(bytes[6] & 0xFF) << 48) |
                ((long)(bytes[7] & 0xFF) << 56);
    }

    @SuppressWarnings("unchecked")
    private void addField(DSFMetadata metadata, String key, String value) {
        if (value == null || value.isEmpty()) return;
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            metadata.addField(new MetadataField<>(key, value, new TextFieldHandler(key)));
        }
    }

    public static class DSFMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();
        private long id3DataOffset = -1;
        private long id3DataSize = 0;

        @Override
        public String getTagFormat() {
            return TagFormat.DSF_METADATA.getFormatName();
        }

        @Override
        public List<MetadataField<?>> getFields() {
            return fields;
        }

        @Override
        public void addField(MetadataField<?> field) {
            fields.add(field);
        }

        public long getId3DataOffset() {
            return id3DataOffset;
        }

        public long getId3DataSize() {
            return id3DataSize;
        }

        void setId3DataOffset(long offset) {
            this.id3DataOffset = offset;
        }

        void setId3DataSize(long size) {
            this.id3DataSize = size;
        }
    }
}