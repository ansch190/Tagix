package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.strategies.parsing.VorbisParsingStrategy;
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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("VorbisParsingStrategy Unit Tests")
class VorbisParsingStrategyTest {

    private VorbisParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new VorbisParsingStrategy();
    }

    private Metadata parseVorbis(byte[] tagData) throws IOException {
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.ogg", tagData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, 0, tagData.length);
        }
    }

    @Test
    @DisplayName("canHandle returns true for VORBIS_COMMENT")
    void canHandle() {
        assertTrue(strategy.canHandle(TagFormat.VORBIS_COMMENT));
    }

    @Test
    @DisplayName("canHandle rejects other formats")
    void canHandleRejectsOtherFormats() {
        assertFalse(strategy.canHandle(TagFormat.ID3V1));
        assertFalse(strategy.canHandle(TagFormat.APEV2));
        assertFalse(strategy.canHandle(TagFormat.MP4));
    }

    @Test
    @DisplayName("Parse OGG Vorbis Comment with header 0x03 + vorbis")
    void parseOggVorbisComment() throws IOException {
        byte[] data = ParsingTestHelper.buildVorbisComment(true, "libVorbis 1.3.7",
                "TITLE=Test Title",
                "ARTIST=Test Artist",
                "ALBUM=Test Album");

        Metadata metadata = parseVorbis(data);

        assertNotNull(metadata);
        assertEquals("Test Title", ParsingTestHelper.findFieldValue(metadata, "TITLE"));
        assertEquals("Test Artist", ParsingTestHelper.findFieldValue(metadata, "ARTIST"));
        assertEquals("Test Album", ParsingTestHelper.findFieldValue(metadata, "ALBUM"));
        assertEquals("libVorbis 1.3.7", ParsingTestHelper.findFieldValue(metadata, "VENDOR"));
    }

    @Test
    @DisplayName("Parse FLAC Vorbis Comment without header")
    void parseFlacVorbisComment() throws IOException {
        byte[] data = ParsingTestHelper.buildVorbisComment(false, "libFLAC 1.3.2",
                "TITLE=Flac Title",
                "ARTIST=Flac Artist");

        Metadata metadata = parseVorbis(data);

        assertNotNull(metadata);
        assertEquals("Flac Title", ParsingTestHelper.findFieldValue(metadata, "TITLE"));
        assertEquals("Flac Artist", ParsingTestHelper.findFieldValue(metadata, "ARTIST"));
        assertEquals("libFLAC 1.3.2", ParsingTestHelper.findFieldValue(metadata, "VENDOR"));
    }

    @Test
    @DisplayName("Multiple values for same key are joined with '; '")
    void multipleValuesForSameKey() throws IOException {
        byte[] data = ParsingTestHelper.buildVorbisComment(false, "vendor",
                "ARTIST=Artist A",
                "ARTIST=Artist B");

        Metadata metadata = parseVorbis(data);

        assertEquals("Artist A; Artist B", ParsingTestHelper.findFieldValue(metadata, "ARTIST"));
    }

    @Test
    @DisplayName("Empty vendor string is skipped")
    void emptyVendor() throws IOException {
        byte[] data = ParsingTestHelper.buildVorbisComment(false, "",
                "TITLE=Test Title");

        Metadata metadata = parseVorbis(data);

        assertNotNull(metadata);
        assertFalse(ParsingTestHelper.hasField(metadata, "VENDOR"));
        assertEquals("Test Title", ParsingTestHelper.findFieldValue(metadata, "TITLE"));
    }

    @Test
    @DisplayName("Corrupt vendor length throws IOException")
    void corruptVendorLength() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ParsingTestHelper.le32(999999));

        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "corrupt.ogg", baos.toByteArray());
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, 0, baos.size()));
        }
    }

    @Test
    @DisplayName("Special characters preserved with UTF-8")
    void specialCharactersUTF8() throws IOException {
        byte[] data = ParsingTestHelper.buildVorbisComment(false, "vendor",
                "TITLE=Título con ñ",
                "ARTIST=Björk",
                "ALBUM=Album mit Ümläüten");

        Metadata metadata = parseVorbis(data);

        assertEquals("Título con ñ", ParsingTestHelper.findFieldValue(metadata, "TITLE"));
        assertEquals("Björk", ParsingTestHelper.findFieldValue(metadata, "ARTIST"));
        assertEquals("Album mit Ümläüten", ParsingTestHelper.findFieldValue(metadata, "ALBUM"));
    }

    @Test
    @DisplayName("Unknown fields are preserved with original key")
    void unknownFields() throws IOException {
        byte[] data = ParsingTestHelper.buildVorbisComment(false, "vendor",
                "CUSTOMFIELD=Custom Value",
                "TITLE=Test Title");

        Metadata metadata = parseVorbis(data);

        assertTrue(ParsingTestHelper.hasField(metadata, "CUSTOMFIELD"));
        assertEquals("Custom Value", ParsingTestHelper.findFieldValue(metadata, "CUSTOMFIELD"));
        assertEquals("Test Title", ParsingTestHelper.findFieldValue(metadata, "TITLE"));
    }
}