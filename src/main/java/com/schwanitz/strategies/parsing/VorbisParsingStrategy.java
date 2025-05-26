package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.VorbisMetadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VorbisParsingStrategy implements TagParsingStrategy {
    private static final Logger Log = LoggerFactory.getLogger(VorbisParsingStrategy.class);

    private final Map<String, FieldHandler<?>> handlers;

    public VorbisParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // Kern-Metadaten
        handlers.put("TITLE", new TextFieldHandler("TITLE"));
        handlers.put("ARTIST", new TextFieldHandler("ARTIST"));
        handlers.put("ALBUM", new TextFieldHandler("ALBUM"));
        handlers.put("DATE", new TextFieldHandler("DATE"));
        handlers.put("TRACKNUMBER", new TextFieldHandler("TRACKNUMBER"));
        handlers.put("GENRE", new TextFieldHandler("GENRE"));
        handlers.put("COMMENT", new TextFieldHandler("COMMENT"));

        // Erweiterte Metadaten
        handlers.put("DESCRIPTION", new TextFieldHandler("DESCRIPTION"));
        handlers.put("ALBUMARTIST", new TextFieldHandler("ALBUMARTIST"));
        handlers.put("COMPOSER", new TextFieldHandler("COMPOSER"));
        handlers.put("PERFORMER", new TextFieldHandler("PERFORMER"));
        handlers.put("COPYRIGHT", new TextFieldHandler("COPYRIGHT"));
        handlers.put("LICENSE", new TextFieldHandler("LICENSE"));
        handlers.put("ORGANIZATION", new TextFieldHandler("ORGANIZATION"));
        handlers.put("LOCATION", new TextFieldHandler("LOCATION"));
        handlers.put("CONTACT", new TextFieldHandler("CONTACT"));

        // Technische Metadaten
        handlers.put("ISRC", new TextFieldHandler("ISRC"));
        handlers.put("DISCNUMBER", new TextFieldHandler("DISCNUMBER"));
        handlers.put("TOTALTRACKS", new TextFieldHandler("TOTALTRACKS"));
        handlers.put("TOTALDISCS", new TextFieldHandler("TOTALDISCS"));

        // Encoder-Metadaten (beide Varianten für Kompatibilität)
        handlers.put("ENCODER", new TextFieldHandler("ENCODER"));
        handlers.put("ENCODED-BY", new TextFieldHandler("ENCODED-BY"));
        handlers.put("ENCODEDBY", new TextFieldHandler("ENCODEDBY"));
        handlers.put("VERSION", new TextFieldHandler("VERSION"));
        handlers.put("VENDOR", new TextFieldHandler("VENDOR"));

        // ReplayGain
        handlers.put("REPLAYGAIN_TRACK_GAIN", new TextFieldHandler("REPLAYGAIN_TRACK_GAIN"));
        handlers.put("REPLAYGAIN_TRACK_PEAK", new TextFieldHandler("REPLAYGAIN_TRACK_PEAK"));
        handlers.put("REPLAYGAIN_ALBUM_GAIN", new TextFieldHandler("REPLAYGAIN_ALBUM_GAIN"));
        handlers.put("REPLAYGAIN_ALBUM_PEAK", new TextFieldHandler("REPLAYGAIN_ALBUM_PEAK"));

        // Erweiterte Standard-Felder
        handlers.put("ORIGINALDATE", new TextFieldHandler("ORIGINALDATE"));
        handlers.put("ORIGINALYEAR", new TextFieldHandler("ORIGINALYEAR"));
        handlers.put("RELEASESTATUS", new TextFieldHandler("RELEASESTATUS"));
        handlers.put("RELEASETYPE", new TextFieldHandler("RELEASETYPE"));

        // MusicBrainz IDs
        handlers.put("MUSICBRAINZ_TRACKID", new TextFieldHandler("MUSICBRAINZ_TRACKID"));
        handlers.put("MUSICBRAINZ_ALBUMID", new TextFieldHandler("MUSICBRAINZ_ALBUMID"));
        handlers.put("MUSICBRAINZ_ARTISTID", new TextFieldHandler("MUSICBRAINZ_ARTISTID"));
        handlers.put("MUSICBRAINZ_ALBUMARTISTID", new TextFieldHandler("MUSICBRAINZ_ALBUMARTISTID"));
        handlers.put("MUSICBRAINZ_RELEASEGROUPID", new TextFieldHandler("MUSICBRAINZ_RELEASEGROUPID"));
        handlers.put("MUSICBRAINZ_WORKID", new TextFieldHandler("MUSICBRAINZ_WORKID"));

        // Sort-Felder
        handlers.put("ALBUMARTISTSORT", new TextFieldHandler("ALBUMARTISTSORT"));
        handlers.put("ARTISTSORT", new TextFieldHandler("ARTISTSORT"));
        handlers.put("ALBUMSORT", new TextFieldHandler("ALBUMSORT"));
        handlers.put("TITLESORT", new TextFieldHandler("TITLESORT"));
        handlers.put("COMPOSERSORT", new TextFieldHandler("COMPOSERSORT"));

        // Audio-Eigenschaften
        handlers.put("BPM", new TextFieldHandler("BPM"));
        handlers.put("KEY", new TextFieldHandler("KEY"));
        handlers.put("MOOD", new TextFieldHandler("MOOD"));
        handlers.put("COMPILATION", new TextFieldHandler("COMPILATION"));

        // Weitere wichtige Felder
        handlers.put("LANGUAGE", new TextFieldHandler("LANGUAGE"));
        handlers.put("SCRIPT", new TextFieldHandler("SCRIPT"));
        handlers.put("MEDIA", new TextFieldHandler("MEDIA"));
        handlers.put("BARCODE", new TextFieldHandler("BARCODE"));
        handlers.put("CATALOGNUMBER", new TextFieldHandler("CATALOGNUMBER"));
        handlers.put("LABEL", new TextFieldHandler("LABEL"));
        handlers.put("LABELNO", new TextFieldHandler("LABELNO"));
        handlers.put("ASIN", new TextFieldHandler("ASIN"));

        // Podcast/Audio-spezifisch
        handlers.put("PODCAST", new TextFieldHandler("PODCAST"));
        handlers.put("PODCASTURL", new TextFieldHandler("PODCASTURL"));
        handlers.put("SUBTITLE", new TextFieldHandler("SUBTITLE"));
        handlers.put("GROUPING", new TextFieldHandler("GROUPING"));
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.VORBIS_COMMENT;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        VorbisMetadata metadata = new VorbisMetadata();
        parseVorbisComment(file, metadata, offset, size);
        return metadata;
    }

    private void parseVorbisComment(RandomAccessFile file, VorbisMetadata metadata, long offset, long size)
            throws IOException {
        file.seek(offset);

        // Für OGG: Packet Type Byte überspringen, falls vorhanden
        byte[] firstByte = new byte[1];
        file.read(firstByte);
        long currentOffset = offset + 1;

        // Prüfen ob es ein OGG Vorbis Comment Header ist (beginnt mit 0x03 + "vorbis")
        if (firstByte[0] == 0x03) {
            byte[] vorbisCheck = new byte[6];
            file.read(vorbisCheck);
            String vorbisString = new String(vorbisCheck, StandardCharsets.US_ASCII);
            if (!"vorbis".equals(vorbisString)) {
                throw new IOException("Invalid Vorbis Comment header");
            }
            currentOffset += 6;
        } else {
            // Zurückspringen - könnte FLAC Vorbis Comment Block sein
            file.seek(offset);
            currentOffset = offset;
        }

        // Vendor String lesen
        String vendor = readVendorString(file);
        Log.debug("Vorbis Comment Vendor: " + vendor);
        currentOffset += 4 + vendor.getBytes(StandardCharsets.UTF_8).length;

        // Vendor String als Metadatum speichern falls vorhanden
        if (!vendor.isEmpty()) {
            addField(metadata, "VENDOR", vendor);
        }

        // User Comment Count lesen (32-bit little-endian)
        long userCommentCount = readLittleEndianUInt32(file);
        currentOffset += 4;

        if (userCommentCount < 0 || userCommentCount > 10000) { // Sanity check
            throw new IOException("Invalid user comment count: " + userCommentCount);
        }

        Log.debug("Reading " + userCommentCount + " Vorbis comments");

        // User Comments lesen
        for (long i = 0; i < userCommentCount; i++) {
            try {
                String comment = readUserComment(file);
                currentOffset += 4 + comment.getBytes(StandardCharsets.UTF_8).length;

                if (!comment.isEmpty()) {
                    parseComment(metadata, comment);
                }

                // Sanity check für Offset
                if (currentOffset > offset + size) {
                    Log.warn("Comment parsing exceeded tag boundary");
                    break;
                }

            } catch (IOException e) {
                Log.warn("Error reading comment " + (i + 1) + ": " + e.getMessage());
                break;
            }
        }

        // Framing bit für OGG (sollte 1 sein)
        if (currentOffset < offset + size) {
            try {
                byte framingBit = file.readByte();
                if (framingBit != 1) {
                    Log.debug("Invalid framing bit: " + framingBit + " (expected 1, normal for FLAC)");
                }
            } catch (IOException e) {
                // Framing bit fehlt - kann bei FLAC vorkommen
                Log.debug("No framing bit found (normal for FLAC)");
            }
        }

        Log.debug("Successfully parsed Vorbis Comment block");
    }

    private String readVendorString(RandomAccessFile file) throws IOException {
        long vendorLength = readLittleEndianUInt32(file);

        if (vendorLength < 0 || vendorLength > 8192) { // Sanity check
            throw new IOException("Invalid vendor string length: " + vendorLength);
        }

        if (vendorLength == 0) {
            return "";
        }

        byte[] vendorBytes = new byte[(int) vendorLength];
        int bytesRead = file.read(vendorBytes);

        if (bytesRead != vendorLength) {
            throw new IOException("Could not read complete vendor string");
        }

        return new String(vendorBytes, StandardCharsets.UTF_8);
    }

    private String readUserComment(RandomAccessFile file) throws IOException {
        long commentLength = readLittleEndianUInt32(file);

        final long MAX_REALISTIC_COMMENT_LENGTH = 16 * 1024 * 1024; // 16 MB

        if (commentLength < 0 || commentLength > MAX_REALISTIC_COMMENT_LENGTH) {
            throw new IOException("Invalid comment length: " + commentLength +
                    " (exceeds realistic limit of " + MAX_REALISTIC_COMMENT_LENGTH + " bytes or is negative)");
        }

        if (commentLength == 0) {
            return "";
        }

        byte[] commentBytes = new byte[(int) commentLength];
        file.readFully(commentBytes); // Diese Methode wirft IOException, wenn nicht alle Bytes gelesen werden können

        return new String(commentBytes, StandardCharsets.UTF_8);
    }

    private long readLittleEndianUInt32(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        int bytesRead = file.read(bytes);

        if (bytesRead != 4) {
            throw new IOException("Could not read 32-bit integer");
        }

        return ((long) (bytes[0] & 0xFF)) |
                ((long) (bytes[1] & 0xFF) << 8) |
                ((long) (bytes[2] & 0xFF) << 16) |
                ((long) (bytes[3] & 0xFF) << 24);
    }

    private void parseComment(VorbisMetadata metadata, String comment) {
        int equalPos = comment.indexOf('=');

        if (equalPos <= 0 || equalPos == comment.length() - 1) {
            Log.debug("Invalid comment format (no = or empty field/value): " + comment);
            return;
        }

        String fieldName = comment.substring(0, equalPos).trim().toUpperCase();
        String fieldValue = comment.substring(equalPos + 1).trim();

        if (fieldName.isEmpty() || fieldValue.isEmpty()) {
            Log.debug("Empty field name or value in comment: " + comment);
            return;
        }

        // Validierung des Feldnamens (nur ASCII Zeichen 0x20-0x7D außer 0x3D)
        if (!isValidFieldName(fieldName)) {
            Log.debug("Invalid field name: " + fieldName);
            return;
        }

        // Multi-Value Support: Prüfe ob Feld bereits existiert
        String existingValue = getExistingFieldValue(metadata, fieldName);
        if (existingValue != null && !existingValue.equals(fieldValue)) {
            // Werte mit Separator verbinden (Vorbis Comment Standard)
            fieldValue = existingValue + "; " + fieldValue;
            Log.debug("Multi-value field detected: " + fieldName + " = " + fieldValue);
        }

        addField(metadata, fieldName, fieldValue);

        Log.debug("Parsed comment: " + fieldName + " = " +
                (fieldValue.length() > 50 ? fieldValue.substring(0, 50) + "..." : fieldValue));
    }

    private String getExistingFieldValue(VorbisMetadata metadata, String fieldName) {
        // Suche nach existierendem Feld mit gleichem Namen
        for (MetadataField<?> field : metadata.getFields()) {
            if (field.getKey().equals(fieldName)) {
                Object value = field.getValue();
                return value != null ? value.toString() : null;
            }
        }
        return null;
    }

    private boolean isValidFieldName(String fieldName) {
        for (char c : fieldName.toCharArray()) {
            if (c < 0x20 || c > 0x7D || c == 0x3D) { // 0x3D ist '='
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void addField(VorbisMetadata metadata, String key, String value) {
        // Normalisiere Feldnamen (case-insensitive)
        String normalizedKey = key.toUpperCase();

        // Bei Multi-Value: Entferne existierendes Feld zuerst
        metadata.getFields().removeIf(field -> field.getKey().equals(normalizedKey));

        FieldHandler<?> handler = handlers.get(normalizedKey);
        if (handler != null) {
            metadata.addField(new MetadataField<>(normalizedKey, value, (FieldHandler<String>) handler));
        } else {
            // Fallback: TextFieldHandler für unbekannte Felder erstellen
            TextFieldHandler textHandler = new TextFieldHandler(normalizedKey);
            metadata.addField(new MetadataField<>(normalizedKey, value, textHandler));
            Log.debug("Created fallback handler for unknown field: " + normalizedKey);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key.toUpperCase(), handler);
    }
}