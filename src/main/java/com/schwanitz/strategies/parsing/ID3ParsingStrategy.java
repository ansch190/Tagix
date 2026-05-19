package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.metadata.GenericMetadata;
import com.schwanitz.metadata.TextFieldHandler;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SourceReader;
import com.schwanitz.strategies.parsing.context.TagParsingStrategy;
import com.schwanitz.strategies.parsing.id3.ID3FrameParser;
import com.schwanitz.strategies.parsing.id3.ID3FrameParserRegistry;
import com.schwanitz.strategies.parsing.id3.ID3FrameParsingUtils;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Parsing-Strategie für ID3-Tags (ID3v1, ID3v1.1, ID3v2.2, ID3v2.3 und ID3v2.4).
 *
 * <p>Diese Strategie liest ID3-Metadaten aus Audiodateien und wandelt sie in {@link ID3Metadata}-Objekte um.
 * ID3v1-Tags werden als fester 128-Byte-Block am Dateiende gelesen, während ID3v2-Tags einen
 * variablen Header gefolgt von Frames verwenden. Für ID3v2-Frames wird ein separates
 * {@link ID3FrameParserRegistry}-System genutzt, das jedem Frame-ID einen spezialisierten Parser zuordnet.</p>
 *
 * <p>Unterstützte Formate:</p>
 * <ul>
 *   <li>{@link TagFormat#ID3V1} – fester 128-Byte-Tag am Dateiende</li>
 *   <li>{@link TagFormat#ID3V1_1} – erweitert um Tracknummer-Feld</li>
 *   <li>{@link TagFormat#ID3V2_2} – 3-Zeichen-Frame-IDs, kein Extended Header</li>
 *   <li>{@link TagFormat#ID3V2_3} – 4-Zeichen-Frame-IDs, 4-Byte-Größen (syncsafe=False)</li>
 *   <li>{@link TagFormat#ID3V2_4} – 4-Zeichen-Frame-IDs, syncsafe-Größen, erweiterter Header</li>
 * </ul>
 *
 * @see TagParsingStrategy
 * @see ID3FrameParserRegistry
 * @see ID3FrameParsingUtils
 */
public class ID3ParsingStrategy extends AbstractTagParsingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ID3ParsingStrategy.class);

    private final ID3FrameParserRegistry frameParserRegistry;

    /**
     * Erzeugt eine neue ID3-Parsing-Strategie mit Standard-Handlern und der Frame-Parser-Registry.
     */
    public ID3ParsingStrategy() {
        super("ID3");
        this.frameParserRegistry = new ID3FrameParserRegistry();
        initializeDefaultHandlers();
    }

    /**
     * Parst ein ID3-Tag aus der angegebenen Datenquelle.
     *
     * @param format das ID3-Format (v1, v1.1, v2.2, v2.3 oder v2.4)
     * @param source die Datenquelle, aus der gelesen wird
     * @param offset der Start-Offset des Tags
     * @param size   die Größe des Tags in Bytes
     * @return die extrahierten {@link GenericMetadata}
     * @throws IOException bei I/O-Fehlern oder ungültigem Tag-Format
     */
    @Override
    public Metadata parseTag(TagFormat format, SeekableDataSource source, long offset, long size) throws IOException {
        TagInfo tagInfo = new TagInfo(format, offset, size);
        return parseID3Tag(source, tagInfo);
    }


    private Metadata parseID3Tag(SeekableDataSource source, TagInfo tagInfo) throws IOException {
        TagFormat format = tagInfo.getFormat();
        long offset = tagInfo.getOffset();
        long size = tagInfo.getSize();

        GenericMetadata metadata = new GenericMetadata(format);

        switch (format) {
            case ID3V1:
            case ID3V1_1:
                parseID3v1(source, metadata, offset, format);
                break;
            case ID3V2_2:
            case ID3V2_3:
            case ID3V2_4:
                parseID3v2(source, metadata, offset, size, format);
                break;
            default:
                throw new IllegalArgumentException("Unsupported ID3 format: " + format);
        }

        return metadata;
    }

    private void parseID3v1(SeekableDataSource source, GenericMetadata metadata, long offset, TagFormat format)
            throws IOException {
        byte[] tagData = new byte[128];
        source.readFully(offset, tagData);

        String header = new String(tagData, 0, 3, StandardCharsets.ISO_8859_1);
        if (!"TAG".equals(header)) {
            throw new IOException("Invalid ID3v1 header");
        }

        String title = ID3FrameParsingUtils.extractFixedString(tagData, 3, 30);
        if (!title.isEmpty()) addField(metadata, "TIT2", title);

        String artist = ID3FrameParsingUtils.extractFixedString(tagData, 33, 30);
        if (!artist.isEmpty()) addField(metadata, "TPE1", artist);

        String album = ID3FrameParsingUtils.extractFixedString(tagData, 63, 30);
        if (!album.isEmpty()) addField(metadata, "TALB", album);

        String year = ID3FrameParsingUtils.extractFixedString(tagData, 93, 4);
        if (!year.isEmpty()) addField(metadata, "TYER", year);

        if (format == TagFormat.ID3V1_1) {
            String comment = ID3FrameParsingUtils.extractFixedString(tagData, 97, 28);
            if (!comment.isEmpty()) addField(metadata, "COMM", comment);

            int track = tagData[126] & 0xFF;
            if (track > 0) addField(metadata, "TRCK", String.valueOf(track));
        } else {
            String comment = ID3FrameParsingUtils.extractFixedString(tagData, 97, 30);
            if (!comment.isEmpty()) addField(metadata, "COMM", comment);
        }

        int genreIndex = tagData[127] & 0xFF;
        if (genreIndex < ID3FrameParsingUtils.ID3V1_GENRES.length) {
            addField(metadata, "TCON", ID3FrameParsingUtils.ID3V1_GENRES[genreIndex]);
        }

        LOG.debug("Successfully parsed ID3v1{} tag", format == TagFormat.ID3V1_1 ? ".1" : "");
    }

    private static final int MAX_ID3_FRAME_SIZE = 16 * 1024 * 1024; // 16 MB safety limit per frame

    private void parseID3v2(SeekableDataSource source, GenericMetadata metadata, long offset, long size, TagFormat format)
            throws IOException {
        SourceReader reader = new SourceReader(source, offset);

        byte[] header = new byte[10];
        reader.readFully(header);

        if (!"ID3".equals(new String(header, 0, 3, StandardCharsets.US_ASCII))) {
            throw new IOException("Invalid ID3v2 header");
        }

        int majorVersion = header[3] & 0xFF;
        int flags = header[5] & 0xFF;

        int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) |
                ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);

        boolean hasExtendedHeader = (flags & 0x40) != 0;
        long frameDataStart = offset + 10;

        if (hasExtendedHeader) {
            reader.seek(frameDataStart);
            byte[] extHeaderSize = new byte[4];
            reader.readFully(extHeaderSize);

            int extSize;
            if (majorVersion == 3) {
                extSize = ((extHeaderSize[0] & 0xFF) << 24) | ((extHeaderSize[1] & 0xFF) << 16) |
                        ((extHeaderSize[2] & 0xFF) << 8) | (extHeaderSize[3] & 0xFF);
            } else {
                extSize = ((extHeaderSize[0] & 0x7F) << 21) | ((extHeaderSize[1] & 0x7F) << 14) |
                        ((extHeaderSize[2] & 0x7F) << 7) | (extHeaderSize[3] & 0x7F);
            }
            frameDataStart += extSize;
        }

        long currentPos = frameDataStart;
        long endPos = offset + 10 + tagSize;

        while (currentPos < endPos - 10) {
            reader.seek(currentPos);

            int frameHeaderSize = (majorVersion == 2) ? 6 : 10;
            byte[] frameHeader = new byte[frameHeaderSize];
            int bytesRead = reader.read(frameHeader);

            if (bytesRead != frameHeaderSize) {
                break;
            }

            String frameId;
            int frameSize;

            if (majorVersion == 2) {
                frameId = new String(frameHeader, 0, 3, StandardCharsets.US_ASCII);
                frameSize = ((frameHeader[3] & 0xFF) << 16) | ((frameHeader[4] & 0xFF) << 8) |
                        (frameHeader[5] & 0xFF);
            } else {
                frameId = new String(frameHeader, 0, 4, StandardCharsets.US_ASCII);
                if (majorVersion == 4) {
                    frameSize = ((frameHeader[4] & 0x7F) << 21) | ((frameHeader[5] & 0x7F) << 14) |
                            ((frameHeader[6] & 0x7F) << 7) | (frameHeader[7] & 0x7F);
                } else {
                    frameSize = ((frameHeader[4] & 0xFF) << 24) | ((frameHeader[5] & 0xFF) << 16) |
                            ((frameHeader[6] & 0xFF) << 8) | (frameHeader[7] & 0xFF);
                }
            }

            if (frameId.contains("\0") || frameSize <= 0 || frameSize > endPos - currentPos || frameSize > MAX_ID3_FRAME_SIZE) {
                break;
            }

            byte[] frameData = new byte[frameSize];
            reader.readFully(frameData);

            parseFrame(metadata, frameId, frameData, majorVersion);

            currentPos += frameHeaderSize + frameSize;
        }

        LOG.debug("Successfully parsed ID3v2.{} tag", majorVersion);
    }

    private void parseFrame(GenericMetadata metadata, String frameId, byte[] frameData, int majorVersion) {
        try {
            if (frameData.length == 0) {
                return;
            }

            ID3FrameParser parser = frameParserRegistry.getParser(frameId);
            if (parser != null) {
                String value = parser.parse(frameData, frameId, majorVersion);
                if (!value.isEmpty()) {
                    addField(metadata, frameId, value);
                }
            } else {
                LOG.debug("Unhandled frame type: {}", frameId);
                // Fallback: als Text-Frame behandeln
                ID3FrameParser textParser = frameParserRegistry.getParser("TIT2");
                if (textParser != null) {
                    String text = textParser.parse(frameData, frameId, majorVersion);
                    if (!text.isEmpty()) {
                        addField(metadata, frameId, text);
                        LOG.debug("Treated unknown frame {} as text frame", frameId);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error parsing frame {}: {}", frameId, e.getMessage());
        }
    }


    private void initializeDefaultHandlers() {
        // ID3v2.3/2.4 Standard-Frames
        handlers.put("TIT1", new TextFieldHandler("TIT1"));
        handlers.put("TIT2", new TextFieldHandler("TIT2"));
        handlers.put("TIT3", new TextFieldHandler("TIT3"));
        handlers.put("TALB", new TextFieldHandler("TALB"));
        handlers.put("TOAL", new TextFieldHandler("TOAL"));
        handlers.put("TRCK", new TextFieldHandler("TRCK"));
        handlers.put("TPOS", new TextFieldHandler("TPOS"));
        handlers.put("TSST", new TextFieldHandler("TSST"));
        handlers.put("TSRC", new TextFieldHandler("TSRC"));

        handlers.put("TPE1", new TextFieldHandler("TPE1"));
        handlers.put("TPE2", new TextFieldHandler("TPE2"));
        handlers.put("TPE3", new TextFieldHandler("TPE3"));
        handlers.put("TPE4", new TextFieldHandler("TPE4"));
        handlers.put("TOPE", new TextFieldHandler("TOPE"));
        handlers.put("TEXT", new TextFieldHandler("TEXT"));
        handlers.put("TOLY", new TextFieldHandler("TOLY"));
        handlers.put("TCOM", new TextFieldHandler("TCOM"));
        handlers.put("TMCL", new TextFieldHandler("TMCL"));
        handlers.put("TIPL", new TextFieldHandler("TIPL"));
        handlers.put("TENC", new TextFieldHandler("TENC"));

        handlers.put("TCOP", new TextFieldHandler("TCOP"));
        handlers.put("TPRO", new TextFieldHandler("TPRO"));
        handlers.put("TPUB", new TextFieldHandler("TPUB"));
        handlers.put("TOWN", new TextFieldHandler("TOWN"));
        handlers.put("TRSN", new TextFieldHandler("TRSN"));
        handlers.put("TRSO", new TextFieldHandler("TRSO"));

        handlers.put("TYER", new TextFieldHandler("TYER"));
        handlers.put("TDAT", new TextFieldHandler("TDAT"));
        handlers.put("TIME", new TextFieldHandler("TIME"));
        handlers.put("TORY", new TextFieldHandler("TORY"));
        handlers.put("TRDA", new TextFieldHandler("TRDA"));
        handlers.put("TDRC", new TextFieldHandler("TDRC"));
        handlers.put("TDRL", new TextFieldHandler("TDRL"));
        handlers.put("TDOR", new TextFieldHandler("TDOR"));
        handlers.put("TDTG", new TextFieldHandler("TDTG"));

        handlers.put("TCON", new TextFieldHandler("TCON"));
        handlers.put("TCAT", new TextFieldHandler("TCAT"));
        handlers.put("TKWD", new TextFieldHandler("TKWD"));
        handlers.put("TDES", new TextFieldHandler("TDES"));

        handlers.put("TBPM", new TextFieldHandler("TBPM"));
        handlers.put("TKEY", new TextFieldHandler("TKEY"));
        handlers.put("TLAN", new TextFieldHandler("TLAN"));
        handlers.put("TLEN", new TextFieldHandler("TLEN"));
        handlers.put("TMED", new TextFieldHandler("TMED"));
        handlers.put("TFLT", new TextFieldHandler("TFLT"));
        handlers.put("TSSE", new TextFieldHandler("TSSE"));

        handlers.put("TMOO", new TextFieldHandler("TMOO"));
        handlers.put("TGID", new TextFieldHandler("TGID"));

        handlers.put("TSOA", new TextFieldHandler("TSOA"));
        handlers.put("TSOP", new TextFieldHandler("TSOP"));
        handlers.put("TSOT", new TextFieldHandler("TSOT"));

        handlers.put("TXXX", new TextFieldHandler("TXXX"));
        handlers.put("COMM", new TextFieldHandler("COMM"));
        handlers.put("USLT", new TextFieldHandler("USLT"));
        handlers.put("SYLT", new TextFieldHandler("SYLT"));

        handlers.put("WCOM", new TextFieldHandler("WCOM"));
        handlers.put("WCOP", new TextFieldHandler("WCOP"));
        handlers.put("WOAF", new TextFieldHandler("WOAF"));
        handlers.put("WOAR", new TextFieldHandler("WOAR"));
        handlers.put("WOAS", new TextFieldHandler("WOAS"));
        handlers.put("WORS", new TextFieldHandler("WORS"));
        handlers.put("WPAY", new TextFieldHandler("WPAY"));
        handlers.put("WPUB", new TextFieldHandler("WPUB"));
        handlers.put("WXXX", new TextFieldHandler("WXXX"));

        handlers.put("APIC", new TextFieldHandler("APIC"));
        handlers.put("GEOB", new TextFieldHandler("GEOB"));
        handlers.put("PCNT", new TextFieldHandler("PCNT"));
        handlers.put("POPM", new TextFieldHandler("POPM"));
        handlers.put("RBUF", new TextFieldHandler("RBUF"));
        handlers.put("AENC", new TextFieldHandler("AENC"));
        handlers.put("LINK", new TextFieldHandler("LINK"));
        handlers.put("POSS", new TextFieldHandler("POSS"));
        handlers.put("USER", new TextFieldHandler("USER"));
        handlers.put("OWNE", new TextFieldHandler("OWNE"));
        handlers.put("PRIV", new TextFieldHandler("PRIV"));

        handlers.put("MVNM", new TextFieldHandler("MVNM"));
        handlers.put("MVIN", new TextFieldHandler("MVIN"));
        handlers.put("GRP1", new TextFieldHandler("GRP1"));

        // ID3v2.2 3-Zeichen-Frames
        handlers.put("TT1", new TextFieldHandler("TT1"));
        handlers.put("TT2", new TextFieldHandler("TT2"));
        handlers.put("TT3", new TextFieldHandler("TT3"));
        handlers.put("TP1", new TextFieldHandler("TP1"));
        handlers.put("TP2", new TextFieldHandler("TP2"));
        handlers.put("TP3", new TextFieldHandler("TP3"));
        handlers.put("TP4", new TextFieldHandler("TP4"));
        handlers.put("TAL", new TextFieldHandler("TAL"));
        handlers.put("TYE", new TextFieldHandler("TYE"));
        handlers.put("TDA", new TextFieldHandler("TDA"));
        handlers.put("TIM", new TextFieldHandler("TIM"));
        handlers.put("TRD", new TextFieldHandler("TRD"));
        handlers.put("TCO", new TextFieldHandler("TCO"));
        handlers.put("TRK", new TextFieldHandler("TRK"));
        handlers.put("TPA", new TextFieldHandler("TPA"));
        handlers.put("TCR", new TextFieldHandler("TCR"));
        handlers.put("TPB", new TextFieldHandler("TPB"));
        handlers.put("TEN", new TextFieldHandler("TEN"));
        handlers.put("TSS", new TextFieldHandler("TSS"));
        handlers.put("TBP", new TextFieldHandler("TBP"));
        handlers.put("TCM", new TextFieldHandler("TCM"));
        handlers.put("TKE", new TextFieldHandler("TKE"));
        handlers.put("TLA", new TextFieldHandler("TLA"));
        handlers.put("TLE", new TextFieldHandler("TLE"));
        handlers.put("TMT", new TextFieldHandler("TMT"));
        handlers.put("TOF", new TextFieldHandler("TOF"));
        handlers.put("TOL", new TextFieldHandler("TOL"));
        handlers.put("TOA", new TextFieldHandler("TOA"));
        handlers.put("TOT", new TextFieldHandler("TOT"));
        handlers.put("TOR", new TextFieldHandler("TOR"));
        handlers.put("TXT", new TextFieldHandler("TXT"));
        handlers.put("TXX", new TextFieldHandler("TXX"));
        handlers.put("COM", new TextFieldHandler("COM"));
        handlers.put("ULT", new TextFieldHandler("ULT"));
        handlers.put("WAF", new TextFieldHandler("WAF"));
        handlers.put("WAR", new TextFieldHandler("WAR"));
        handlers.put("WAS", new TextFieldHandler("WAS"));
        handlers.put("WCM", new TextFieldHandler("WCM"));
        handlers.put("WCP", new TextFieldHandler("WCP"));
        handlers.put("WPB", new TextFieldHandler("WPB"));
        handlers.put("WXX", new TextFieldHandler("WXX"));
        handlers.put("PIC", new TextFieldHandler("PIC"));
        handlers.put("GEO", new TextFieldHandler("GEO"));
        handlers.put("CNT", new TextFieldHandler("CNT"));
        handlers.put("POP", new TextFieldHandler("POP"));
        handlers.put("BUF", new TextFieldHandler("BUF"));
        handlers.put("CRA", new TextFieldHandler("CRA"));
        handlers.put("LNK", new TextFieldHandler("LNK"));
    }
}
