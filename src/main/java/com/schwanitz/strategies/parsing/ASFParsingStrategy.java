package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SourceReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsing-Strategie für ASF-Metadaten (Advanced Systems Format).
 *
 * <p>ASF-Dateien (Windows Media) verwenden GUID-basierte Objekte zur Speicherung von Metadaten.
 * Diese Strategie unterstützt drei relevante Objekttypen:</p>
 * <ul>
 *   <li><b>Content Description Object</b> – einfache Felder wie Titel, Autor, Copyright, Beschreibung
 *       und Bewertung alsnull-terminierte UTF-16LE-Strings mit Längenangaben</li>
 *   <li><b>Extended Content Description Object</b> – erweiterte Felder mit lokalisierten Namen
 *       und verschiedenen Datentypen (String, DWORD, QWORD, WORD, Bool)</li>
 *   <li><b>Metadata Object / Metadata Library Object</b> – ähnlich Extended Content Description,
 *       wird auf dieselbe Weise geparst</li>
 * </ul>
 *
 * <p>Unterstützte Formate:</p>
 * <ul>
 *   <li>{@link TagFormat#ASF_CONTENT_DESC}</li>
 *   <li>{@link TagFormat#ASF_EXT_CONTENT_DESC}</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public class ASFParsingStrategy extends AbstractTagParsingStrategy {

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



    /**
     * Erzeugt eine neue ASF-Parsing-Strategie mit Standard-Handlern für Content Description
     * und Extended Content Description-Felder.
     */
    public ASFParsingStrategy() {
        super("ASF");
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

    /**
     * Parst ASF-Metadaten aus der angegebenen Datei.
     *
     * <p>Erkennt anhand der GUID am Offset, ob es sich um ein Content Description Object,
     * Extended Content Description Object, Metadata Object oder Metadata Library Object handelt,
     * und delegiert an die entsprechende Parse-Methode.</p>
     *
     * @param format das ASF-Format
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des ASF-Objekts
     * @param size   die Größe des Objekts in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder ungültigem ASF-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(format);

        byte[] guid = new byte[GUID_SIZE];
        source.readFully(offset, guid);

        SourceReader reader = new SourceReader(source, offset);

        if (java.util.Arrays.equals(guid, CONTENT_DESC_GUID)) {
            parseContentDescription(reader, metadata, offset, size);
        } else if (java.util.Arrays.equals(guid, EXT_CONTENT_DESC_GUID)) {
            parseExtendedContentDescription(reader, metadata, offset, size);
        } else if (java.util.Arrays.equals(guid, METADATA_GUID) || java.util.Arrays.equals(guid, METADATA_LIBRARY_GUID)) {
            parseMetadataObject(reader, metadata, offset, size);
        }

        return metadata;
    }

    private void parseContentDescription(SourceReader reader, GenericMetadata metadata, long offset, long size)
            throws IOException {
        reader.seek(offset + GUID_SIZE + 8);

        byte[] lengths = new byte[10];
        reader.readFully(lengths);

        int titleLen = BinaryDataReader.readLittleEndianInt16(lengths, TITLE_LENGTH_OFFSET);
        int authorLen = BinaryDataReader.readLittleEndianInt16(lengths, AUTHOR_LENGTH_OFFSET);
        int copyrightLen = BinaryDataReader.readLittleEndianInt16(lengths, COPYRIGHT_LENGTH_OFFSET);
        int descLen = BinaryDataReader.readLittleEndianInt16(lengths, DESCRIPTION_LENGTH_OFFSET);
        int ratingLen = BinaryDataReader.readLittleEndianInt16(lengths, RATING_LENGTH_OFFSET);

        if (titleLen > 0 && titleLen <= 65536) {
            byte[] titleBytes = new byte[titleLen];
            reader.readFully(titleBytes);
            addField(metadata, "Title", decodeASFString(titleBytes), true, false, false);
        }
        if (authorLen > 0 && authorLen <= 65536) {
            byte[] authorBytes = new byte[authorLen];
            reader.readFully(authorBytes);
            addField(metadata, "Artist", decodeASFString(authorBytes), true, false, false);
        }
        if (copyrightLen > 0 && copyrightLen <= 65536) {
            byte[] copyrightBytes = new byte[copyrightLen];
            reader.readFully(copyrightBytes);
            addField(metadata, "Copyright", decodeASFString(copyrightBytes), true, false, false);
        }
        if (descLen > 0 && descLen <= 65536) {
            byte[] descBytes = new byte[descLen];
            reader.readFully(descBytes);
            addField(metadata, "Description", decodeASFString(descBytes), true, false, false);
        }
        if (ratingLen > 0 && ratingLen <= 65536) {
            byte[] ratingBytes = new byte[ratingLen];
            reader.readFully(ratingBytes);
            addField(metadata, "Rating", decodeASFString(ratingBytes), true, false, false);
        }
    }

    private void parseExtendedContentDescription(SourceReader reader, GenericMetadata metadata, long offset, long size)
            throws IOException {
        reader.seek(offset + GUID_SIZE + 8);

        int count = BinaryDataReader.readLittleEndianInt16(reader);

        for (int i = 0; i < count; i++) {
            int nameLen = BinaryDataReader.readLittleEndianInt16(reader);
            if (nameLen <= 0 || nameLen > 65536) break;

            byte[] nameBytes = new byte[nameLen];
            reader.readFully(nameBytes);
            String name = decodeASFString(nameBytes);

            int dataType = BinaryDataReader.readLittleEndianInt16(reader);
            int dataLen = BinaryDataReader.readLittleEndianInt16(reader);

            if (dataLen <= 0 || dataLen > 65536) break;

            byte[] data = new byte[dataLen];
            reader.readFully(data);

            String fieldName = KNOWN_EXT_CONTENT_FIELDS.getOrDefault(name, name);

            switch (dataType) {
                case ASF_TYPE_STRING:
                    addField(metadata, fieldName, decodeASFString(data), true, false, false);
                    break;
                case ASF_TYPE_DWORD:
                    if (data.length >= 4) {
                        addField(metadata, fieldName, String.valueOf(BinaryDataReader.readLittleEndianInt32(data, 0)), true, false, false);
                    }
                    break;
                case ASF_TYPE_QWORD:
                    if (data.length >= 8) {
                        addField(metadata, fieldName, String.valueOf(BinaryDataReader.readLittleEndianInt64(data, 0)), true, false, false);
                    }
                    break;
                case ASF_TYPE_WORD:
                    if (data.length >= 2) {
                        addField(metadata, fieldName, String.valueOf(BinaryDataReader.readLittleEndianInt16(data, 0)), true, false, false);
                    }
                    break;
                case ASF_TYPE_BOOL:
                    if (data.length >= 4) {
                        addField(metadata, fieldName, Boolean.toString(BinaryDataReader.readLittleEndianInt32(data, 0) != 0), true, false, false);
                    }
                    break;
                default:
                    LOG.debug("Unknown ASF data type {} for field {}", dataType, name);
                    break;
            }
        }
    }

    private void parseMetadataObject(SourceReader reader, GenericMetadata metadata, long offset, long size)
            throws IOException {
        parseExtendedContentDescription(reader, metadata, offset, size);
    }

    private String decodeASFString(byte[] data) {
        if (data.length < 2) return "";
        String raw = new String(data, 0, data.length, StandardCharsets.UTF_16LE);
        int nullIdx = raw.indexOf('\0');
        return nullIdx >= 0 ? raw.substring(0, nullIdx).trim() : raw.trim();
    }



}