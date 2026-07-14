package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import static com.schwanitz.formats.matroska.MatroskaConstants.*;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SourceReader;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsing-Strategie für Matroska/WebM-Tag-Metadaten.
 *
 * <p>Matroska-Dateien (MKV) und WebM-Dateien verwenden eine EBML-basierte (Extensible Binary
 * Markup Language) Struktur zur Speicherung von Metadaten. Tags befinden sich im {@code Tags}-Element
 * und bestehen aus verschachtelten {@code Tag}- und {@code SimpleTag}-Elementen. Jeder SimpleTag
 * enthält einen {@code TagName} (Schlüssel) und einen {@code TagString} (Wert). Element-IDs und
 * Größen werden als Variable-Length-Integer (VLI) kodiert.</p>
 *
 * <p>Unterstützte Formate:</p>
 * <ul>
 *   <li>{@link TagFormat#MATROSKA_TAGS} – MKV-Dateien</li>
 *   <li>{@link TagFormat#WEBM_TAGS} – WebM-Dateien</li>
 * </ul>
 *
 * @see TagParsingStrategy
 */
public class MatroskaParsingStrategy extends AbstractTagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MatroskaParsingStrategy.class);

    private static final long TAG_ELEMENT_ID = 0x7373L;
    private static final long SIMPLE_TAG_ID = 0x67C8L;
    private static final long TAG_NAME_ID = 0x45A3L;
    private static final long TAG_STRING_ID = 0x4487L;
    private static final long TAG_LANGUAGE_ID = 0x447AL;
    private static final long TAG_DEFAULT_ID = 0x4484L;
    private static final long TARGETS_ELEMENT_ID = 0x63C0L;

    private static final Map<String, String> MATROSKA_FIELD_MAPPING = new HashMap<>();

    static {
        MATROSKA_FIELD_MAPPING.put("TITLE", "Title");
        MATROSKA_FIELD_MAPPING.put("ARTIST", "Artist");
        MATROSKA_FIELD_MAPPING.put("ALBUM", "Album");
        MATROSKA_FIELD_MAPPING.put("DATE", "Date");
        MATROSKA_FIELD_MAPPING.put("GENRE", "Genre");
        MATROSKA_FIELD_MAPPING.put("COMMENT", "Comment");
        MATROSKA_FIELD_MAPPING.put("TRACKNUMBER", "TrackNumber");
        MATROSKA_FIELD_MAPPING.put("MUSICBRAINZ_TRACKID", "MusicBrainzTrackId");
        MATROSKA_FIELD_MAPPING.put("MUSICBRAINZ_ARTISTID", "MusicBrainzArtistId");
        MATROSKA_FIELD_MAPPING.put("MUSICBRAINZ_ALBUMID", "MusicBrainzAlbumId");
        MATROSKA_FIELD_MAPPING.put("REPLAYGAIN_TRACK_GAIN", "ReplayGainTrackGain");
        MATROSKA_FIELD_MAPPING.put("REPLAYGAIN_TRACK_PEAK", "ReplayGainTrackPeak");
        MATROSKA_FIELD_MAPPING.put("REPLAYGAIN_ALBUM_GAIN", "ReplayGainAlbumGain");
        MATROSKA_FIELD_MAPPING.put("REPLAYGAIN_ALBUM_PEAK", "ReplayGainAlbumPeak");
        MATROSKA_FIELD_MAPPING.put("ORIGINAL", "OriginalTitle");
        MATROSKA_FIELD_MAPPING.put("ENCODER", "Encoder");
        MATROSKA_FIELD_MAPPING.put("ENCODER_SETTINGS", "EncoderSettings");
        MATROSKA_FIELD_MAPPING.put("BPM", "BPM");
        MATROSKA_FIELD_MAPPING.put("RATING", "Rating");
        MATROSKA_FIELD_MAPPING.put("PRODUCER", "Producer");
        MATROSKA_FIELD_MAPPING.put("COMPOSER", "Composer");
        MATROSKA_FIELD_MAPPING.put("CONDUCTOR", "Conductor");
        MATROSKA_FIELD_MAPPING.put("LABEL", "Label");
    }



    /**
     * Erzeugt eine neue Matroska-Parsing-Strategie mit Standard-Handlern für alle bekannten Matroska-Tag-Felder.
     */
    public MatroskaParsingStrategy() {
        super("Matroska");
        for (String fieldName : MATROSKA_FIELD_MAPPING.values()) {
            handlers.put(fieldName, new TextFieldHandler(fieldName));
        }
    }

    /**
     * Parst Matroska/WebM-Tag-Metadaten aus der angegebenen Datenquelle.
     *
     * <p>Sucht nach dem {@code Tags}-Element am angegebenen Offset und iteriert über alle
     * {@code Tag}- und {@code SimpleTag}-Sub-Elemente, um Schlüssel-Wert-Paare zu extrahieren.</p>
     *
     * @param format das Matroska- oder WebM-Tag-Format
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des Tags-Elements
     * @param size   die Größe des Elements in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder ungültiger EBML-Struktur
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        GenericMetadata metadata = new GenericMetadata(format);

        SourceReader reader = new SourceReader(source, offset);

        byte[] tagId = new byte[4];
        reader.read(tagId);

        if (!Arrays.equals(tagId, TAGS_ID)) {
            LOG.debug("Not a Tags element at offset {}", offset);
            return metadata;
        }

        long tagsSize = readVLI(reader);

        long dataStart = reader.getFilePointer();
        long dataEnd = dataStart + tagsSize;

        long currentPos = dataStart;

        while (currentPos < dataEnd) {
            reader.seek(currentPos);

            long elementId = readVLI(reader);
            if (elementId < 0) break;

            long elementSize = readVLI(reader);
            if (elementSize < 0 || reader.getFilePointer() + elementSize > dataEnd) break;

            if (elementId == TAG_ELEMENT_ID) {
                parseTagElement(reader, metadata, reader.getFilePointer(), elementSize);
            }

            currentPos = reader.getFilePointer() + elementSize;
            if (elementId != TAG_ELEMENT_ID) {
                reader.seek(currentPos);
            }
        }

        return metadata;
    }

    private void parseTagElement(SourceReader reader, GenericMetadata metadata, long start, long size) throws IOException {
        long endPos = start + size;
        long currentPos = start;

        while (currentPos < endPos) {
            reader.seek(currentPos);

            long elementId = readVLI(reader);
            if (elementId < 0) break;

            long elementSize = readVLI(reader);
            if (elementSize < 0 || reader.getFilePointer() + elementSize > endPos) break;

            if (elementId == SIMPLE_TAG_ID) {
                parseSimpleTag(reader, metadata, reader.getFilePointer(), elementSize);
                currentPos = reader.getFilePointer();
            } else {
                currentPos = reader.getFilePointer() + elementSize;
            }
        }
    }

    private void parseSimpleTag(SourceReader reader, GenericMetadata metadata, long start, long size) throws IOException {
        long endPos = start + size;
        long currentPos = start;
        String tagName = null;
        String tagValue = null;

        while (currentPos < endPos) {
            reader.seek(currentPos);

            long elementId = readVLI(reader);
            if (elementId < 0) break;

            long elementSize = readVLI(reader);
            if (elementSize < 0) break;

            long elementEnd = reader.getFilePointer() + elementSize;
            if (elementEnd > endPos) break;

            if (elementId == TAG_NAME_ID && elementSize > 0 && elementSize < 1024) {
                byte[] nameBytes = new byte[(int)elementSize];
                reader.read(nameBytes);
                tagName = new String(nameBytes, StandardCharsets.UTF_8).trim().toUpperCase();
            } else if (elementId == TAG_STRING_ID && elementSize > 0 && elementSize < 65536) {
                byte[] valueBytes = new byte[(int)elementSize];
                reader.read(valueBytes);
                tagValue = new String(valueBytes, StandardCharsets.UTF_8).trim();
            }

            currentPos = elementEnd;
        }

        if (tagName != null && tagValue != null && !tagValue.isEmpty()) {
            String fieldName = MATROSKA_FIELD_MAPPING.getOrDefault(tagName, tagName);
            addField(metadata, fieldName, tagValue, true, false, false);
        }
    }

    private long readVLI(SourceReader reader) throws IOException {
        int firstByte = reader.read();
        if (firstByte == -1) {
            return -1;
        }

        int length = 0;
        int mask = VLI_LEADING_BIT_MASK;

        for (int i = 0; i < 8; i++) {
            if ((firstByte & mask) != 0) {
                length = i + 1;
                break;
            }
            mask >>= 1;
        }

        if (length == 0) {
            return -1;
        }

        long value = firstByte & (0xFF >> length);

        for (int i = 1; i < length; i++) {
            int nextByte = reader.read();
            if (nextByte == -1) {
                return -1;
            }
            value = (value << 8) | nextByte;
        }

        return value;
    }


}
