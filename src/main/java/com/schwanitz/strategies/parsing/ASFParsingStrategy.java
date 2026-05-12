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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ASFParsingStrategy implements TagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ASFParsingStrategy.class);

    // ASF GUIDs
    private static final byte[] CONTENT_DESC_GUID = {
            0x33, 0x26, (byte)0xB2, 0x75, (byte)0x8E, 0x66, (byte)0xCF, 0x11,
            (byte)0xA6, (byte)0xD9, 0x00, (byte)0xAA, 0x00, 0x62, (byte)0xCE, 0x6C
    };
    private static final byte[] EXT_CONTENT_DESC_GUID = {
            0x40, (byte)0xA4, (byte)0xD0, (byte)0xD2, 0x07, (byte)0xE3, (byte)0xD2, 0x11,
            (byte)0x97, (byte)0xF0, 0x00, (byte)0xA0, (byte)0xC9, 0x5E, (byte)0xA8, 0x50
    };
    private static final byte[] METADATA_GUID = {
            (byte)0xEA, (byte)0xCB, (byte)0xF8, (byte)0xC5, (byte)0xAF, 0x5B, 0x77, 0x48,
            (byte)0x84, 0x67, (byte)0xAA, (byte)0x8C, 0x44, (byte)0xFA, 0x4C, (byte)0xCA
    };
    private static final byte[] METADATA_LIBRARY_GUID = {
            (byte)0x94, 0x1C, 0x23, 0x44, (byte)0x98, (byte)0x94, (byte)0xD1, 0x49,
            (byte)0xA1, 0x41, 0x1D, 0x13, 0x4E, 0x45, 0x70, 0x54
    };

    private static final int GUID_SIZE = 16;
    private static final int ASF_OBJECT_MIN_SIZE = 24;

    // ASF Content Description field offsets
    private static final int TITLE_LENGTH_OFFSET = 0;
    private static final int AUTHOR_LENGTH_OFFSET = 2;
    private static final int COPYRIGHT_LENGTH_OFFSET = 4;
    private static final int DESCRIPTION_LENGTH_OFFSET = 6;
    private static final int RATING_LENGTH_OFFSET = 8;

    // ASF Extended Content Description data types
    private static final int ASF_TYPE_STRING = 0;
    private static final int ASF_TYPE_DWORD = 3;
    static final int ASF_TYPE_QWORD = 4;
    static final int ASF_TYPE_WORD = 5;
    static final int ASF_TYPE_BOOL = 6;

    private static final Map<String, String> KNOWN_CONTENT_DESC_FIELDS = new HashMap<>();
    private static final Map<String, String> KNOWN_EXT_CONTENT_FIELDS = new HashMap<>();

    static {
        KNOWN_CONTENT_DESC_FIELDS.put("Title", "Title");
        KNOWN_CONTENT_DESC_FIELDS.put("Author", "Artist");
        KNOWN_CONTENT_DESC_FIELDS.put("Copyright", "Copyright");
        KNOWN_CONTENT_DESC_FIELDS.put("Description", "Description");
        KNOWN_CONTENT_DESC_FIELDS.put("Rating", "Rating");

        KNOWN_EXT_CONTENT_FIELDS.put("WM/AlbumTitle", "Album");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/TrackNumber", "TrackNumber");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/Year", "Year");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/Genre", "Genre");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/Composer", "Composer");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/Conductor", "Conductor");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/Producer", "Producer");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/MediaClassPrimaryID", "MediaClass");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/Mood", "Mood");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/AlbumArtist", "AlbumArtist");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/PartOfSet", "DiscNumber");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/Publisher", "Publisher");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/OriginalAlbumTitle", "OriginalAlbum");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/OriginalArtist", "OriginalArtist");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/ButtonText", "ButtonText");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/EncodedBy", "EncodedBy");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/EncodingTime", "EncodingTime");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/AuthorURL", "AuthorURL");
        KNOWN_EXT_CONTENT_FIELDS.put("WM/UserWebURL", "UserWebURL");
    }

    private final Map<String, FieldHandler<?>> handlers = new HashMap<>();

    public ASFParsingStrategy() {
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        for (String fieldName : KNOWN_CONTENT_DESC_FIELDS.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
        for (String fieldName : KNOWN_EXT_CONTENT_FIELDS.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.ASF_CONTENT_DESC || format == TagFormat.ASF_EXT_CONTENT_DESC;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        ASFMetadata metadata = new ASFMetadata(format);

        file.seek(offset);

        byte[] guid = new byte[GUID_SIZE];
        file.read(guid);

        if (java.util.Arrays.equals(guid, CONTENT_DESC_GUID)) {
            parseContentDescription(file, metadata, offset, size);
        } else if (java.util.Arrays.equals(guid, EXT_CONTENT_DESC_GUID)) {
            parseExtendedContentDescription(file, metadata, offset, size);
        } else if (java.util.Arrays.equals(guid, METADATA_GUID) || java.util.Arrays.equals(guid, METADATA_LIBRARY_GUID)) {
            parseMetadataObject(file, metadata, offset, size);
        }

        return metadata;
    }

    private void parseContentDescription(RandomAccessFile file, ASFMetadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset + GUID_SIZE + 8);

        byte[] lengths = new byte[10];
        file.read(lengths);

        int titleLen = readLittleEndianInt16(lengths, TITLE_LENGTH_OFFSET);
        int authorLen = readLittleEndianInt16(lengths, AUTHOR_LENGTH_OFFSET);
        int copyrightLen = readLittleEndianInt16(lengths, COPYRIGHT_LENGTH_OFFSET);
        int descLen = readLittleEndianInt16(lengths, DESCRIPTION_LENGTH_OFFSET);
        int ratingLen = readLittleEndianInt16(lengths, RATING_LENGTH_OFFSET);

        if (titleLen > 0) {
            byte[] titleBytes = new byte[titleLen];
            file.read(titleBytes);
            addField(metadata, "Title", decodeASFString(titleBytes));
        }
        if (authorLen > 0) {
            byte[] authorBytes = new byte[authorLen];
            file.read(authorBytes);
            addField(metadata, "Artist", decodeASFString(authorBytes));
        }
        if (copyrightLen > 0) {
            byte[] copyrightBytes = new byte[copyrightLen];
            file.read(copyrightBytes);
            addField(metadata, "Copyright", decodeASFString(copyrightBytes));
        }
        if (descLen > 0) {
            byte[] descBytes = new byte[descLen];
            file.read(descBytes);
            addField(metadata, "Description", decodeASFString(descBytes));
        }
        if (ratingLen > 0) {
            byte[] ratingBytes = new byte[ratingLen];
            file.read(ratingBytes);
            addField(metadata, "Rating", decodeASFString(ratingBytes));
        }
    }

    private void parseExtendedContentDescription(RandomAccessFile file, ASFMetadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset + GUID_SIZE + 8);

        int count = readLittleEndianInt16(file);

        for (int i = 0; i < count; i++) {
            int nameLen = readLittleEndianInt16(file);
            if (nameLen <= 0 || nameLen > 65536) break;

            byte[] nameBytes = new byte[nameLen];
            file.read(nameBytes);
            String name = decodeASFString(nameBytes);

            int dataType = readLittleEndianInt16(file);
            int dataLen = readLittleEndianInt16(file);

            if (dataLen <= 0 || dataLen > 65536) break;

            byte[] data = new byte[dataLen];
            file.read(data);

            String fieldName = KNOWN_EXT_CONTENT_FIELDS.getOrDefault(name, name);

            switch (dataType) {
                case ASF_TYPE_STRING:
                    addField(metadata, fieldName, decodeASFString(data));
                    break;
                case ASF_TYPE_DWORD:
                    if (data.length >= 4) {
                        addField(metadata, fieldName, String.valueOf(readLittleEndianInt32(data, 0)));
                    }
                    break;
                case ASF_TYPE_QWORD:
                    if (data.length >= 8) {
                        addField(metadata, fieldName, String.valueOf(readLittleEndianInt64(data, 0)));
                    }
                    break;
                case ASF_TYPE_WORD:
                    if (data.length >= 2) {
                        addField(metadata, fieldName, String.valueOf(readLittleEndianInt16(data, 0)));
                    }
                    break;
                case ASF_TYPE_BOOL:
                    if (data.length >= 4) {
                        addField(metadata, fieldName, readLittleEndianInt32(data, 0) != 0 ? "true" : "false");
                    }
                    break;
                default:
                    LOG.debug("Unknown ASF data type {} for field {}", dataType, name);
                    break;
            }
        }
    }

    private void parseMetadataObject(RandomAccessFile file, ASFMetadata metadata, long offset, long size)
            throws IOException {
        parseExtendedContentDescription(file, metadata, offset, size);
    }

    private String decodeASFString(byte[] data) {
        if (data.length < 2) return "";
        String raw = new String(data, 0, data.length, StandardCharsets.UTF_16LE);
        int nullIdx = raw.indexOf('\0');
        return nullIdx >= 0 ? raw.substring(0, nullIdx).trim() : raw.trim();
    }

    private int readLittleEndianInt16(RandomAccessFile file) throws IOException {
        byte[] b = new byte[2];
        file.read(b);
        return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8);
    }

    private int readLittleEndianInt16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private int readLittleEndianInt32(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private long readLittleEndianInt64(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF)) |
                ((long)(data[offset + 1] & 0xFF) << 8) |
                ((long)(data[offset + 2] & 0xFF) << 16) |
                ((long)(data[offset + 3] & 0xFF) << 24) |
                ((long)(data[offset + 4] & 0xFF) << 32) |
                ((long)(data[offset + 5] & 0xFF) << 40) |
                ((long)(data[offset + 6] & 0xFF) << 48) |
                ((long)(data[offset + 7] & 0xFF) << 56);
    }

    @SuppressWarnings("unchecked")
    private void addField(ASFMetadata metadata, String key, String value) {
        if (value == null || value.isEmpty()) return;
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            metadata.addField(new MetadataField<>(key, value, new TextFieldHandler(key)));
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    public static class ASFMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();
        private final TagFormat format;

        public ASFMetadata(TagFormat format) {
            this.format = format;
        }

        @Override
        public String getTagFormat() {
            return format.getFormatName();
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