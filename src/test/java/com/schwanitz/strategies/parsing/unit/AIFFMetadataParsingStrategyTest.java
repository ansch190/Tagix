package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.AIFFMetadataParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static com.schwanitz.strategies.parsing.ParsingTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class AIFFMetadataParsingStrategyTest {

    private AIFFMetadataParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new AIFFMetadataParsingStrategy();
    }

    @Test
    @DisplayName("canHandle returns true for AIFF_METADATA")
    void canHandle_AIFF_true() {
        assertTrue(strategy.canHandle(TagFormat.AIFF_METADATA));
    }

    @Test
    @DisplayName("canHandle returns false for non-AIFF formats")
    void canHandle_nonAIFF_false() {
        assertFalse(strategy.canHandle(TagFormat.MP4));
        assertFalse(strategy.canHandle(TagFormat.RIFF_INFO));
        assertFalse(strategy.canHandle(TagFormat.ID3V2_3));
    }

    @Test
    @DisplayName("parseTextChunk extracts NAME, AUTH, ANNO, (c) chunks")
    void parseTextChunk() throws IOException {
        byte[] nameChunk = buildAIFFTextChunk("NAME", "Test Song");
        byte[] authChunk = buildAIFFTextChunk("AUTH", "Test Artist");
        byte[] annoChunk = buildAIFFTextChunk("ANNO", "Test Annotation");
        byte[] copyChunk = buildAIFFTextChunk("(c) ", "Test Copyright");

        File nameFile = writeTempFile(tempDir.toFile(), "name.aiff", nameChunk);
        File authFile = writeTempFile(tempDir.toFile(), "auth.aiff", authChunk);
        File annoFile = writeTempFile(tempDir.toFile(), "anno.aiff", annoChunk);
        File copyFile = writeTempFile(tempDir.toFile(), "copy.aiff", copyChunk);

        try (RandomAccessFile raf = new RandomAccessFile(nameFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, nameChunk.length);
            assertEquals("Test Song", ParsingTestHelper.findFieldValue(metadata, "Title"));
        }
        try (RandomAccessFile raf = new RandomAccessFile(authFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, authChunk.length);
            assertEquals("Test Artist", ParsingTestHelper.findFieldValue(metadata, "Artist"));
        }
        try (RandomAccessFile raf = new RandomAccessFile(annoFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, annoChunk.length);
            assertEquals("Test Annotation", ParsingTestHelper.findFieldValue(metadata, "Annotation"));
        }
        try (RandomAccessFile raf = new RandomAccessFile(copyFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, copyChunk.length);
            assertEquals("Test Copyright", ParsingTestHelper.findFieldValue(metadata, "Copyright"));
        }
    }

    @Test
    @DisplayName("parseCommentChunk extracts structured COMT chunk")
    void parseCommentChunk() throws IOException {
        byte[] commentData = new byte[2 + 8 + 2 + 5 + 1];
        int pos = 0;
        commentData[pos++] = 0; commentData[pos++] = 1;
        commentData[pos++] = 0; commentData[pos++] = 0; commentData[pos++] = 0; commentData[pos++] = 0;
        commentData[pos++] = 0; commentData[pos++] = 1;
        commentData[pos++] = 0; commentData[pos++] = 5;
        commentData[pos++] = 'H'; commentData[pos++] = 'e'; commentData[pos++] = 'l'; commentData[pos++] = 'l'; commentData[pos++] = 'o';

        byte[] comtChunk = buildAIFFChunk("COMT", commentData);
        File file = writeTempFile(tempDir.toFile(), "comt.aiff", comtChunk);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, comtChunk.length);

            assertTrue(ParsingTestHelper.hasField(metadata, "Comment"));
        }
    }

    @Test
    @DisplayName("parseApplicationChunk extracts APPL with stoc signature")
    void parseApplicationChunk() throws IOException {
        byte[] appData = concat(ascii("stoc"), ascii("TestAppData"));
        byte[] applChunk = buildAIFFChunk("APPL", appData);
        File file = writeTempFile(tempDir.toFile(), "appl.aiff", applChunk);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, applChunk.length);

            String appValue = ParsingTestHelper.findFieldValue(metadata, "Application");
            assertNotNull(appValue);
            assertTrue(appValue.contains("stoc"));
        }
    }

    @Test
    @DisplayName("skipID3Chunk skips ID3 chunk without error")
    void skipID3Chunk() throws IOException {
        byte[] id3Data = concat(ascii("ID3 "), be32(10), new byte[]{0x03, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        byte[] id3Chunk = buildAIFFChunk("ID3 ", id3Data);
        File file = writeTempFile(tempDir.toFile(), "id3.aiff", id3Chunk);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, id3Chunk.length);

            assertNotNull(metadata);
            assertFalse(ParsingTestHelper.hasField(metadata, "ID3"));
        }
    }

    @Test
    @DisplayName("invalidChunkSize throws IOException for negative or too large sizes")
    void invalidChunkSize_throwsIOException() throws IOException {
        byte[] data = concat(ascii("NAME"), be32(-1));
        File file = writeTempFile(tempDir.toFile(), "invalid.aiff", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.AIFF_METADATA, raf, 0, data.length));
        }
    }
}