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

public class DFFParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DFFParsingStrategy.class);

    private static final byte[] DFF_FORM_SIGNATURE = {'F', 'R', 'M', '8'};
    private static final byte[] DFF_DSD_TYPE = {'D', 'S', 'D', ' '};
    private static final byte[] DFF_DIIN_CHUNK = {'D', 'I', 'I', 'N'};
    private static final byte[] DFF_DITI_CHUNK = {'D', 'I', 'T', 'I'};
    private static final int DFF_CHUNK_HEADER_SIZE = 12;

    private static final Map<String, String> DFF_FIELD_MAPPING = new HashMap<>();

    static {
        DFF_FIELD_MAPPING.put("DSD ", "Format");
        DFF_FIELD_MAPPING.put("DITI", "Title");
        DFF_FIELD_MAPPING.put("DIAR", "Artist");
        DFF_FIELD_MAPPING.put("DIAL", "Album");
        DFF_FIELD_MAPPING.put("DICN", "Comment");
        DFF_FIELD_MAPPING.put("DIRC", "Copyright");
        DFF_FIELD_MAPPING.put("DIDT", "Date");
        DFF_FIELD_MAPPING.put("DIGR", "Genre");
        DFF_FIELD_MAPPING.put("DIBT", "TrackNumber");
    }

    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();

    public DFFParsingStrategy() {
        for (String fieldName : DFF_FIELD_MAPPING.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.DFF_METADATA;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        DFFMetadata metadata = new DFFMetadata();

        file.seek(offset);

        byte[] chunkId = new byte[4];
        file.read(chunkId);
        long chunkSize = readBigEndianLong(file);

        if (Arrays.equals(chunkId, DFF_DIIN_CHUNK)) {
            parseDIINChunk(file, metadata, offset, chunkSize);
        } else if (Arrays.equals(chunkId, DFF_DITI_CHUNK)) {
            parseDITIChunk(file, metadata, chunkSize);
        } else {
            LOG.debug("Unknown DFF metadata chunk: {} at offset {}", new String(chunkId, StandardCharsets.US_ASCII), offset);
        }

        return metadata;
    }

    private void parseDIINChunk(RandomAccessFile file, DFFMetadata metadata, long offset, long chunkSize) throws IOException {
        long endPos = offset + DFF_CHUNK_HEADER_SIZE + chunkSize;
        long currentPos = offset + DFF_CHUNK_HEADER_SIZE;

        while (currentPos + DFF_CHUNK_HEADER_SIZE < endPos) {
            file.seek(currentPos);

            byte[] subChunkId = new byte[4];
            file.read(subChunkId);

            long subChunkSize = readBigEndianLong(file);

            if (subChunkSize < 0 || currentPos + DFF_CHUNK_HEADER_SIZE + subChunkSize > endPos) {
                break;
            }

            String chunkName = new String(subChunkId, StandardCharsets.US_ASCII);

            if (Arrays.equals(subChunkId, DFF_DITI_CHUNK)) {
                parseDITIChunk(file, metadata, subChunkSize);
            } else if (DFF_FIELD_MAPPING.containsKey(chunkName)) {
                parseTextSubChunk(file, metadata, subChunkId, subChunkSize);
            }

            currentPos += DFF_CHUNK_HEADER_SIZE + subChunkSize;
            if (subChunkSize % 2 != 0) {
                currentPos++;
            }
        }
    }

    private void parseDITIChunk(RandomAccessFile file, DFFMetadata metadata, long chunkSize) throws IOException {
        if (chunkSize <= 0) return;

        byte[] data = new byte[(int)Math.min(chunkSize, 65536)];
        file.read(data);
        String title = new String(data, StandardCharsets.UTF_8).trim();

        int nullIdx = title.indexOf('\0');
        if (nullIdx >= 0) title = title.substring(0, nullIdx);

        if (!title.isEmpty()) {
            addField(metadata, "Title", title);
        }
    }

    private void parseTextSubChunk(RandomAccessFile file, DFFMetadata metadata, byte[] chunkId, long chunkSize) throws IOException {
        if (chunkSize <= 0) return;

        String chunkName = new String(chunkId, StandardCharsets.US_ASCII);
        String fieldName = DFF_FIELD_MAPPING.getOrDefault(chunkName, chunkName);

        byte[] data = new byte[(int)Math.min(chunkSize, 65536)];
        file.read(data);
        String text = new String(data, StandardCharsets.UTF_8).trim();

        int nullIdx = text.indexOf('\0');
        if (nullIdx >= 0) text = text.substring(0, nullIdx);

        if (!text.isEmpty()) {
            addField(metadata, fieldName, text);
        }
    }

    private long readBigEndianLong(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[8];
        file.read(bytes);
        return ((long)(bytes[0] & 0xFF) << 56) |
                ((long)(bytes[1] & 0xFF) << 48) |
                ((long)(bytes[2] & 0xFF) << 40) |
                ((long)(bytes[3] & 0xFF) << 32) |
                ((long)(bytes[4] & 0xFF) << 24) |
                ((long)(bytes[5] & 0xFF) << 16) |
                ((long)(bytes[6] & 0xFF) << 8) |
                ((long)(bytes[7] & 0xFF));
    }

    @SuppressWarnings("unchecked")
    private void addField(DFFMetadata metadata, String key, String value) {
        if (value == null || value.isEmpty()) return;
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            metadata.addField(new MetadataField<>(key, value, new TextFieldHandler(key)));
        }
    }

    public static class DFFMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();

        @Override
        public String getTagFormat() {
            return TagFormat.DFF_METADATA.getFormatName();
        }

        @Override
        public List<MetadataField<?>> getFields() {
            return fields;
        }

        @Override
        public void addField(MetadataField<?> field) {
            fields.add(field);
        }
    }
}