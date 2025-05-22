package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.FieldHandler;
import com.schwanitz.interfaces.Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.others.TextFieldHandler;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.tagging.TagFormat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class BWFParsingStrategy implements TagParsingStrategy {
    private static final Logger LOGGER = Logger.getLogger(BWFParsingStrategy.class.getName());

    // BWF Chunk Struktur
    private static final int BWF_DESCRIPTION_SIZE = 256;
    private static final int BWF_ORIGINATOR_SIZE = 32;
    private static final int BWF_ORIGINATOR_REF_SIZE = 32;
    private static final int BWF_DATE_SIZE = 10;
    private static final int BWF_TIME_SIZE = 8;
    private static final int BWF_UMID_SIZE = 64;
    private static final int BWF_LOUDNESS_SIZE = 180; // For version 2

    private final Map<String, FieldHandler<?>> handlers;

    public BWFParsingStrategy() {
        this.handlers = new HashMap<>();
        initializeDefaultHandlers();
    }

    private void initializeDefaultHandlers() {
        // BWF Standard Felder
        handlers.put("Description", new TextFieldHandler("Description"));
        handlers.put("Originator", new TextFieldHandler("Originator"));
        handlers.put("OriginatorReference", new TextFieldHandler("OriginatorReference"));
        handlers.put("OriginationDate", new TextFieldHandler("OriginationDate"));
        handlers.put("OriginationTime", new TextFieldHandler("OriginationTime"));
        handlers.put("TimeReference", new TextFieldHandler("TimeReference"));
        handlers.put("Version", new TextFieldHandler("Version"));
        handlers.put("UMID", new TextFieldHandler("UMID"));
        handlers.put("LoudnessValue", new TextFieldHandler("LoudnessValue"));
        handlers.put("LoudnessRange", new TextFieldHandler("LoudnessRange"));
        handlers.put("MaxTruePeakLevel", new TextFieldHandler("MaxTruePeakLevel"));
        handlers.put("MaxMomentaryLoudness", new TextFieldHandler("MaxMomentaryLoudness"));
        handlers.put("MaxShortTermLoudness", new TextFieldHandler("MaxShortTermLoudness"));
        handlers.put("CodingHistory", new TextFieldHandler("CodingHistory"));
    }

    @Override
    public boolean canHandle(TagFormat format) {
        return format == TagFormat.BWF_V0 || format == TagFormat.BWF_V1 || format == TagFormat.BWF_V2;
    }

    @Override
    public Metadata parseTag(TagFormat format, RandomAccessFile file, long offset, long size) throws IOException {
        BWFMetadata metadata = new BWFMetadata(format);
        parseBWFChunk(file, metadata, offset, size, format);
        return metadata;
    }

    private void parseBWFChunk(RandomAccessFile file, BWFMetadata metadata, long offset, long size, TagFormat format)
            throws IOException {
        file.seek(offset);

        // bext Chunk Header lesen
        byte[] chunkHeader = new byte[8];
        file.read(chunkHeader);

        String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
        if (!"bext".equals(chunkId)) {
            throw new IOException("Expected bext chunk, found: " + chunkId);
        }

        int chunkSize = readLittleEndianInt32(chunkHeader, 4);
        LOGGER.fine("Parsing BWF bext chunk with size: " + chunkSize);

        // Mindestgröße prüfen (602 Bytes für Version 0)
        if (chunkSize < 602) {
            throw new IOException("BWF chunk too small: " + chunkSize + " bytes");
        }

        // Description (256 bytes, null-padded)
        String description = readFixedString(file, BWF_DESCRIPTION_SIZE);
        if (!description.isEmpty()) {
            addField(metadata, "Description", description);
        }

        // Originator (32 bytes, null-padded)
        String originator = readFixedString(file, BWF_ORIGINATOR_SIZE);
        if (!originator.isEmpty()) {
            addField(metadata, "Originator", originator);
        }

        // Originator Reference (32 bytes, null-padded)
        String originatorRef = readFixedString(file, BWF_ORIGINATOR_REF_SIZE);
        if (!originatorRef.isEmpty()) {
            addField(metadata, "OriginatorReference", originatorRef);
        }

        // Origination Date (10 bytes, YYYY-MM-DD format)
        String originationDate = readFixedString(file, BWF_DATE_SIZE);
        if (!originationDate.isEmpty() && isValidDate(originationDate)) {
            addField(metadata, "OriginationDate", originationDate);
        }

        // Origination Time (8 bytes, HH:MM:SS format)
        String originationTime = readFixedString(file, BWF_TIME_SIZE);
        if (!originationTime.isEmpty() && isValidTime(originationTime)) {
            addField(metadata, "OriginationTime", originationTime);
        }

        // Time Reference (8 bytes, little-endian 64-bit)
        long timeReference = readLittleEndianInt64(file);
        addField(metadata, "TimeReference", String.valueOf(timeReference));

        // Version (2 bytes, little-endian 16-bit)
        int version = readLittleEndianInt16(file);
        addField(metadata, "Version", String.valueOf(version));

        // UMID (64 bytes)
        String umid = readUMID(file);
        if (!umid.isEmpty()) {
            addField(metadata, "UMID", umid);
        }

        // Version-spezifische Felder
        if (format == TagFormat.BWF_V2 && chunkSize >= 602 + BWF_LOUDNESS_SIZE) {
            // Loudness Info (180 bytes für Version 2)
            parseLoudnessInfo(file, metadata);
        } else {
            // Reserved (190 bytes für Version 0/1)
            file.skipBytes(190);
        }

        // Coding History (variable length)
        int codingHistorySize = chunkSize - 602;
        if (format == TagFormat.BWF_V2) {
            codingHistorySize -= BWF_LOUDNESS_SIZE;
        }

        if (codingHistorySize > 0) {
            String codingHistory = readCodingHistory(file, codingHistorySize);
            if (!codingHistory.isEmpty()) {
                addField(metadata, "CodingHistory", codingHistory);
            }
        }

        LOGGER.fine("Successfully parsed BWF " + format.getFormatName() + " chunk");
    }

    private void parseLoudnessInfo(RandomAccessFile file, BWFMetadata metadata) throws IOException {
        // Loudness Value (2 bytes, signed little-endian, in LUFS * 100)
        int loudnessValue = readLittleEndianInt16(file);
        if (loudnessValue != -32768) { // -32768 = undefined
            addField(metadata, "LoudnessValue", String.format("%.2f LUFS", loudnessValue / 100.0));
        }

        // Loudness Range (2 bytes, unsigned little-endian, in LU * 100)
        int loudnessRange = readLittleEndianUInt16(file);
        if (loudnessRange != 0) {
            addField(metadata, "LoudnessRange", String.format("%.2f LU", loudnessRange / 100.0));
        }

        // Max True Peak Level (2 bytes, signed little-endian, in dBTP * 100)
        int maxTruePeak = readLittleEndianInt16(file);
        if (maxTruePeak != -32768) { // -32768 = undefined
            addField(metadata, "MaxTruePeakLevel", String.format("%.2f dBTP", maxTruePeak / 100.0));
        }

        // Max Momentary Loudness (2 bytes, signed little-endian, in LUFS * 100)
        int maxMomentary = readLittleEndianInt16(file);
        if (maxMomentary != -32768) {
            addField(metadata, "MaxMomentaryLoudness", String.format("%.2f LUFS", maxMomentary / 100.0));
        }

        // Max Short-term Loudness (2 bytes, signed little-endian, in LUFS * 100)
        int maxShortTerm = readLittleEndianInt16(file);
        if (maxShortTerm != -32768) {
            addField(metadata, "MaxShortTermLoudness", String.format("%.2f LUFS", maxShortTerm / 100.0));
        }

        // Reserved (170 bytes)
        file.skipBytes(170);
    }

    private String readFixedString(RandomAccessFile file, int size) throws IOException {
        byte[] data = new byte[size];
        file.read(data);

        // Finde das erste Null-Byte oder verwende die ganze Länge
        int length = size;
        for (int i = 0; i < size; i++) {
            if (data[i] == 0) {
                length = i;
                break;
            }
        }

        if (length == 0) {
            return "";
        }

        return new String(data, 0, length, StandardCharsets.UTF_8).trim();
    }

    private String readUMID(RandomAccessFile file) throws IOException {
        byte[] umidData = new byte[BWF_UMID_SIZE];
        file.read(umidData);

        // Prüfe ob UMID gesetzt ist (nicht alle Nullen)
        boolean hasContent = false;
        for (byte b : umidData) {
            if (b != 0) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            return "";
        }

        // UMID als Hex-String formatieren
        StringBuilder sb = new StringBuilder();
        for (byte b : umidData) {
            sb.append(String.format("%02X", b & 0xFF));
        }

        return sb.toString();
    }

    private String readCodingHistory(RandomAccessFile file, int size) throws IOException {
        if (size <= 0) {
            return "";
        }

        byte[] data = new byte[size];
        file.read(data);

        // Coding History ist UTF-8 Text
        String history = new String(data, StandardCharsets.UTF_8).trim();

        // Entferne trailing nulls
        while (history.endsWith("\0")) {
            history = history.substring(0, history.length() - 1);
        }

        return history;
    }

    private boolean isValidDate(String date) {
        // YYYY-MM-DD Format prüfen
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private boolean isValidTime(String time) {
        // HH:MM:SS Format prüfen
        return time.matches("\\d{2}:\\d{2}:\\d{2}");
    }

    private int readLittleEndianInt16(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[2];
        file.read(bytes);
        return ((bytes[0] & 0xFF)) | ((bytes[1] & 0xFF) << 8);
    }

    private int readLittleEndianUInt16(RandomAccessFile file) throws IOException {
        return readLittleEndianInt16(file) & 0xFFFF;
    }

    private int readLittleEndianInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private long readLittleEndianInt64(RandomAccessFile file) throws IOException {
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
    private void addField(BWFMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            LOGGER.fine("Created fallback handler for unknown BWF field: " + key);
        }
    }

    public void registerHandler(String key, FieldHandler<?> handler) {
        handlers.put(key, handler);
    }

    // Innere Klasse für BWF Metadata
    public static class BWFMetadata implements Metadata {
        private final List<MetadataField<?>> fields = new ArrayList<>();
        private final TagFormat format;

        public BWFMetadata(TagFormat format) {
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