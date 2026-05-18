package com.schwanitz.strategies.parsing.bwf;

import com.schwanitz.io.BinaryDataReader;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.MetadataField;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.interfaces.FieldHandler;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BWFExtensionParser {

    private static final Logger LOG = LoggerFactory.getLogger(BWFExtensionParser.class);

    private static final String BEXT_CUE_LIST = "cue ";
    private static final String BEXT_PEAK_ENVELOPE = "levl";
    private static final String BEXT_IXML = "iXML";
    private static final String BEXT_AXML = "axml";
    private static final String BEXT_LINK = "link";

    private final Map<String, FieldHandler<?>> handlers;

    public BWFExtensionParser(Map<String, FieldHandler<?>> handlers) {
        this.handlers = handlers;
    }

    public void parseBWFExtensions(RandomAccessFile file, GenericMetadata metadata, long offset, long size) {
        long currentPos = offset;
        long endPos = offset + size;

        LOG.debug("Parsing BWF extensions from position {} to {}", currentPos, endPos);

        while (currentPos + 8 < endPos) {
            try {
                file.seek(currentPos);

                byte[] chunkHeader = new byte[8];
                int bytesRead = file.read(chunkHeader);
                if (bytesRead != 8) {
                    break;
                }

                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = BinaryDataReader.readLittleEndianInt32(chunkHeader, 4);

                if (chunkSize < 0 || chunkSize > endPos - currentPos - 8) {
                    LOG.debug("Invalid chunk size for {}: {}", chunkId, chunkSize);
                    break;
                }

                switch (chunkId) {
                    case BEXT_CUE_LIST:
                        parseCueListChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_PEAK_ENVELOPE:
                        parsePeakEnvelopeChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_IXML:
                        parseIXMLChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_AXML:
                        parseAdobeXMLChunk(file, metadata, chunkSize);
                        break;

                    case BEXT_LINK:
                        parseLinkChunk(file, metadata, chunkSize);
                        break;

                    default:
                        LOG.debug("Unknown BWF extension chunk: {}", chunkId);
                        file.skipBytes(chunkSize);
                        break;
                }

                currentPos += 8 + chunkSize;

                if (chunkSize % 2 != 0 && currentPos < endPos) {
                    file.skipBytes(1);
                    currentPos++;
                }

            } catch (Exception e) {
                LOG.warn("Error parsing BWF extension at position {}: {}", currentPos, e.getMessage());
                break;
            }
        }
    }

    private void parseCueListChunk(RandomAccessFile file, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize < 4) {
            return;
        }

        int numCues = BinaryDataReader.readLittleEndianInt32(file);

        StringBuilder cueInfo = new StringBuilder();
        cueInfo.append("Cue Points: ").append(numCues);

        int bytesRead = 4;
        for (int i = 0; i < numCues && bytesRead + 24 <= chunkSize; i++) {
            int cueId = BinaryDataReader.readLittleEndianInt32(file);
            int position = BinaryDataReader.readLittleEndianInt32(file);

            file.skipBytes(12);

            int sampleOffset = BinaryDataReader.readLittleEndianInt32(file);
            bytesRead += 24;

            cueInfo.append("; Cue").append(i + 1).append(":ID=").append(cueId)
                    .append(",Pos=").append(position).append(",Offset=").append(sampleOffset);
        }

        addField(metadata, "CuePoints", cueInfo.toString());
        LOG.debug("Parsed BWF Cue List with {} cue points", numCues);
    }

    private void parsePeakEnvelopeChunk(RandomAccessFile file, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize < 20) {
            return;
        }

        int version = BinaryDataReader.readLittleEndianInt32(file);
        int format = BinaryDataReader.readLittleEndianInt32(file);
        int pointsPerValue = BinaryDataReader.readLittleEndianInt32(file);
        int blockSize = BinaryDataReader.readLittleEndianInt32(file);
        int channels = BinaryDataReader.readLittleEndianInt32(file);

        int numFrames = (chunkSize - 20) / (channels * 4);

        String peakInfo = String.format("Peak Envelope: Version=%d, Format=%d, PointsPerValue=%d, " +
                        "BlockSize=%d, Channels=%d, Frames=%d",
                version, format, pointsPerValue, blockSize, channels, numFrames);

        addField(metadata, "PeakEnvelope", peakInfo);

        file.skipBytes(chunkSize - 20);

        LOG.debug("Parsed BWF Peak Envelope: {} channels, {} frames", channels, numFrames);
    }

    private void parseIXMLChunk(RandomAccessFile file, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        byte[] xmlData = new byte[chunkSize];
        file.read(xmlData);

        String xmlContent = new String(xmlData, StandardCharsets.UTF_8).trim();

        if (xmlContent.startsWith("<?xml") || xmlContent.startsWith("<BWFXML>")) {
            String extractedInfo = extractIXMLInfo(xmlContent);
            addField(metadata, "iXMLMetadata", extractedInfo);
            LOG.debug("Parsed iXML metadata: {} characters", extractedInfo.length());
        } else {
            addField(metadata, "iXMLMetadata", "[iXML:" + chunkSize + " bytes]");
        }
    }

    private void parseAdobeXMLChunk(RandomAccessFile file, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        byte[] xmlData = new byte[chunkSize];
        file.read(xmlData);

        String xmlContent = new String(xmlData, StandardCharsets.UTF_8).trim();

        if (xmlContent.contains("adobe") || xmlContent.contains("xmp")) {
            String extractedInfo = extractAdobeXMLInfo(xmlContent);
            addField(metadata, "AdobeXMLMetadata", extractedInfo);
            LOG.debug("Parsed Adobe XML metadata: {} characters", extractedInfo.length());
        } else {
            addField(metadata, "AdobeXMLMetadata", "[Adobe XML:" + chunkSize + " bytes]");
        }
    }

    private void parseLinkChunk(RandomAccessFile file, GenericMetadata metadata, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return;
        }

        byte[] linkData = new byte[chunkSize];
        file.read(linkData);

        String linkContent = new String(linkData, StandardCharsets.UTF_8).trim();

        String linkInfo = parseLinkContent(linkContent);
        addField(metadata, "LinkedFiles", linkInfo);

        LOG.debug("Parsed BWF Link chunk: {}", linkInfo);
    }

    private String extractIXMLInfo(String xmlContent) {
        StringBuilder info = new StringBuilder();
        info.append("iXML: ");

        String[] patterns = {
                "PROJECT=\"([^\"]+)\"", "SCENE=\"([^\"]+)\"", "TAPE=\"([^\"]+)\"",
                "TAKE=\"([^\"]+)\"", "NOTE=\"([^\"]+)\"", "SOUND_ROLL=\"([^\"]+)\""
        };

        String[] fieldNames = {"Project", "Scene", "Tape", "Take", "Note", "SoundRoll"};

        for (int i = 0; i < patterns.length; i++) {
            Pattern pattern = Pattern.compile(patterns[i]);
            Matcher matcher = pattern.matcher(xmlContent);
            if (matcher.find()) {
                if (info.length() > 6) info.append(", ");
                info.append(fieldNames[i]).append("=").append(matcher.group(1));
            }
        }

        if (info.length() == 6) {
            info.append("Present (").append(xmlContent.length()).append(" characters)");
        }

        return info.toString();
    }

    private String extractAdobeXMLInfo(String xmlContent) {
        StringBuilder info = new StringBuilder();
        info.append("Adobe XMP: ");

        if (xmlContent.contains("dc:title")) {
            info.append("Title present, ");
        }
        if (xmlContent.contains("dc:creator")) {
            info.append("Creator present, ");
        }
        if (xmlContent.contains("xmp:CreateDate")) {
            info.append("CreateDate present, ");
        }

        String result = info.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }

        if (result.equals("Adobe XMP: ")) {
            result = "Adobe XMP: Present (" + xmlContent.length() + " characters)";
        }

        return result;
    }

    private String parseLinkContent(String linkContent) {
        if (linkContent.isEmpty()) {
            return "No linked files";
        }

        String[] lines = linkContent.split("[\r\n]+");
        StringBuilder files = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && (line.contains(".wav") || line.contains(".bwf") || line.contains("/"))) {
                if (!files.isEmpty()) files.append("; ");
                files.append(line);
            }
        }

        if (files.isEmpty()) {
            return "Link data: " + linkContent.length() + " characters";
        }

        return "Linked files: " + files;
    }

    @SuppressWarnings("unchecked")
    private void addField(GenericMetadata metadata, String key, String value) {
        FieldHandler<?> handler = handlers.get(key);
        if (handler != null) {
            metadata.addField(new MetadataField<>(key, value, (FieldHandler<String>) handler));
        } else {
            TextFieldHandler textHandler = new TextFieldHandler(key);
            metadata.addField(new MetadataField<>(key, value, textHandler));
            LOG.debug("Created fallback handler for unknown BWF extension field: {}", key);
        }
    }
}