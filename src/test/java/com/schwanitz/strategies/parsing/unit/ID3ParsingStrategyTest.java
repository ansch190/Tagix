package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.ID3ParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("ID3ParsingStrategy Unit Tests")
class ID3ParsingStrategyTest {

    private ID3ParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new ID3ParsingStrategy();
    }

    private Metadata parseID3(byte[] tagData, TagFormat format) throws IOException {
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.id3", tagData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            return strategy.parseTag(format, source, 0, tagData.length);
        }
    }

    private Metadata parseID3v1(byte[] tagData, TagFormat format) throws IOException {
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.id3", tagData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            return strategy.parseTag(format, source, 0, tagData.length);
        }
    }

    @Test
    @DisplayName("Parse ID3v1 tag with standard fields")
    void parseID3v1Basic() throws IOException {
        byte[] tagData = ParsingTestHelper.buildID3v1Tag(
                "My Title", "My Artist", "My Album", "2024", "My Comment", (byte) 0, (byte) 17);

        Metadata metadata = parseID3v1(tagData, TagFormat.ID3V1);

        assertNotNull(metadata);
        assertEquals("My Title", ParsingTestHelper.findFieldValue(metadata, "TIT2"));
        assertEquals("My Artist", ParsingTestHelper.findFieldValue(metadata, "TPE1"));
        assertEquals("My Album", ParsingTestHelper.findFieldValue(metadata, "TALB"));
        assertEquals("2024", ParsingTestHelper.findFieldValue(metadata, "TYER"));
        assertEquals("My Comment", ParsingTestHelper.findFieldValue(metadata, "COMM"));
        assertEquals("Rock", ParsingTestHelper.findFieldValue(metadata, "TCON"));
    }

    @Test
    @DisplayName("Parse ID3v1.1 tag with track number")
    void parseID3v1_1WithTrack() throws IOException {
        byte[] tagData = ParsingTestHelper.buildID3v1Tag(
                "Track Title", "Track Artist", "Track Album", "2023", "Short Comment", (byte) 7, (byte) 17);

        Metadata metadata = parseID3v1(tagData, TagFormat.ID3V1_1);

        assertNotNull(metadata);
        assertEquals("7", ParsingTestHelper.findFieldValue(metadata, "TRCK"));
        assertEquals("Rock", ParsingTestHelper.findFieldValue(metadata, "TCON"));
    }

    @Test
    @DisplayName("ID3v1 fields with empty strings are not added to metadata")
    void parseID3v1WithEmptyFields() throws IOException {
        byte[] tagData = ParsingTestHelper.buildID3v1Tag(
                "", "", "", "", "", (byte) 0, (byte) 17);

        Metadata metadata = parseID3v1(tagData, TagFormat.ID3V1);

        assertNotNull(metadata);
        assertFalse(ParsingTestHelper.hasField(metadata, "TIT2"));
        assertFalse(ParsingTestHelper.hasField(metadata, "TPE1"));
        assertFalse(ParsingTestHelper.hasField(metadata, "TALB"));
        assertFalse(ParsingTestHelper.hasField(metadata, "TYER"));
        assertFalse(ParsingTestHelper.hasField(metadata, "COMM"));
        assertEquals("Rock", ParsingTestHelper.findFieldValue(metadata, "TCON"));
    }

    @Test
    @DisplayName("ID3v1 tag with missing TAG header throws IOException")
    void parseID3v1Corrupt() throws IOException {
        byte[] badData = new byte[128];

        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "corrupt.id3", badData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.ID3V1, source, 0, badData.length));
        }
    }

    @Test
    @DisplayName("Parse ID3v2.3 tag with ISO-8859-1 text frames")
    void parseID3v2_3Basic() throws IOException {
        byte[] frame1 = ParsingTestHelper.buildID3v2TextFrame(3, "TIT2", 0, "ISO Title");
        byte[] frame2 = ParsingTestHelper.buildID3v2TextFrame(3, "TPE1", 0, "ISO Artist");
        byte[] frame3 = ParsingTestHelper.buildID3v2TextFrame(3, "TALB", 0, "ISO Album");
        byte[] framesData = ParsingTestHelper.concat(frame1, frame2, frame3);
        byte[] header = ParsingTestHelper.buildID3v2Header(3, 0, framesData.length);
        byte[] fullTag = ParsingTestHelper.concat(header, framesData);

        Metadata metadata = parseID3(fullTag, TagFormat.ID3V2_3);

        assertNotNull(metadata);
        assertEquals("ISO Title", ParsingTestHelper.findFieldValue(metadata, "TIT2"));
        assertEquals("ISO Artist", ParsingTestHelper.findFieldValue(metadata, "TPE1"));
        assertEquals("ISO Album", ParsingTestHelper.findFieldValue(metadata, "TALB"));
    }

    @Test
    @DisplayName("Parse ID3v2.4 tag with UTF-8 text frames")
    void parseID3v2_4Basic() throws IOException {
        byte[] frame1 = ParsingTestHelper.buildID3v2TextFrame(4, "TIT2", 3, "UTF-8 Title: 你好");
        byte[] frame2 = ParsingTestHelper.buildID3v2TextFrame(4, "TPE1", 3, "Björk");
        byte[] framesData = ParsingTestHelper.concat(frame1, frame2);
        byte[] header = ParsingTestHelper.buildID3v2Header(4, 0, framesData.length);
        byte[] fullTag = ParsingTestHelper.concat(header, framesData);

        Metadata metadata = parseID3(fullTag, TagFormat.ID3V2_4);

        assertNotNull(metadata);
        assertEquals("UTF-8 Title: 你好", ParsingTestHelper.findFieldValue(metadata, "TIT2"));
        assertEquals("Björk", ParsingTestHelper.findFieldValue(metadata, "TPE1"));
    }

    @Test
    @DisplayName("Parse ID3v2.2 tag with 3-character frame IDs")
    void parseID3v2_2Basic() throws IOException {
        byte[] frame1 = ParsingTestHelper.buildID3v2TextFrame(2, "TT2", 0, "Title v2.2");
        byte[] frame2 = ParsingTestHelper.buildID3v2TextFrame(2, "TP1", 0, "Artist v2.2");
        byte[] framesData = ParsingTestHelper.concat(frame1, frame2);
        byte[] header = ParsingTestHelper.buildID3v2Header(2, 0, framesData.length);
        byte[] fullTag = ParsingTestHelper.concat(header, framesData);

        Metadata metadata = parseID3(fullTag, TagFormat.ID3V2_2);

        assertNotNull(metadata);
        assertEquals("Title v2.2", ParsingTestHelper.findFieldValue(metadata, "TT2"));
        assertEquals("Artist v2.2", ParsingTestHelper.findFieldValue(metadata, "TP1"));
    }

    @Test
    @DisplayName("ID3v2.3 with extended header skips header and parses frames")
    void parseID3v2_3WithExtendedHeader() throws IOException {
        byte[] frameData = ParsingTestHelper.buildID3v2TextFrame(3, "TIT2", 0, "Extended Title");
        byte[] extHeader = ParsingTestHelper.concat(
                ParsingTestHelper.be32(10),
                new byte[]{0, 0},
                ParsingTestHelper.be32(0)
        );
        int totalSize = extHeader.length + frameData.length;
        byte[] header = ParsingTestHelper.buildID3v2Header(3, 0x40, totalSize);
        byte[] fullTag = ParsingTestHelper.concat(header, extHeader, frameData);

        Metadata metadata = parseID3(fullTag, TagFormat.ID3V2_3);

        assertNotNull(metadata);
        assertEquals("Extended Title", ParsingTestHelper.findFieldValue(metadata, "TIT2"));
    }

    @Test
    @DisplayName("ID3v2 with invalid frame size is handled gracefully")
    void parseID3v2WithInvalidFrameSize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ParsingTestHelper.ascii("TIT2"), 0, 4);
        baos.write(ParsingTestHelper.be32(99999), 0, 4);
        baos.write(new byte[]{0, 0}, 0, 2);
        baos.write(0);
        baos.write(ParsingTestHelper.ascii("Test"), 0, 4);

        byte[] badFrame = baos.toByteArray();
        byte[] header = ParsingTestHelper.buildID3v2Header(3, 0, badFrame.length);
        byte[] fullTag = ParsingTestHelper.concat(header, badFrame);

        Metadata metadata = parseID3(fullTag, TagFormat.ID3V2_3);
        assertNotNull(metadata);
    }

    @Test
    @DisplayName("ID3v1 genre byte 17 maps to Rock")
    void parseID3v1GenreMapping() throws IOException {
        byte[] tagData = ParsingTestHelper.buildID3v1Tag(
                "Song", "Artist", "Album", "2024", "Comment", (byte) 0, (byte) 17);

        Metadata metadata = parseID3v1(tagData, TagFormat.ID3V1);

        assertEquals("Rock", ParsingTestHelper.findFieldValue(metadata, "TCON"));
    }
}