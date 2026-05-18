package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.Lyrics3ParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class Lyrics3ParsingStrategyTest {

    private Lyrics3ParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new Lyrics3ParsingStrategy();
    }

    @Test
    @DisplayName("canHandle returns true for LYRICS3V1 and LYRICS3V2")
    void canHandle_Lyrics3_true() {
        assertTrue(strategy.canHandle(TagFormat.LYRICS3V1));
        assertTrue(strategy.canHandle(TagFormat.LYRICS3V2));
    }

    @Test
    @DisplayName("canHandle returns false for non-Lyrics3 formats")
    void canHandle_nonLyrics3_false() {
        assertFalse(strategy.canHandle(TagFormat.ID3V2_3));
        assertFalse(strategy.canHandle(TagFormat.MP4));
        assertFalse(strategy.canHandle(TagFormat.APEV2));
    }

    @Test
    @DisplayName("parseLyrics3v1Basic extracts basic lyrics between markers")
    void parseLyrics3v1Basic() throws IOException {
        byte[] data = ParsingTestHelper.buildLyrics3v1Tag("Hello world lyrics");
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.LYRICS3V1, raf, 0, data.length);

            String lyrics = ParsingTestHelper.findFieldValue(metadata, "LYRICS");
            assertNotNull(lyrics);
            assertTrue(lyrics.contains("Hello world lyrics"));
        }
    }

    @Test
    @DisplayName("parseLyrics3v2Basic extracts fields with 3-char IDs and 2-digit sizes")
    void parseLyrics3v2Basic() throws IOException {
        byte[] data = ParsingTestHelper.buildLyrics3v2Tag("LYR", "Test lyrics content");
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.LYRICS3V2, raf, 0, data.length);

            String lyrics = ParsingTestHelper.findFieldValue(metadata, "LYR");
            assertNotNull(lyrics);
            assertTrue(lyrics.contains("Test lyrics content"));
        }
    }

    @Test
    @DisplayName("parseLyrics3v2WithMultipleFields extracts LYR, INF, AUT fields")
    void parseLyrics3v2WithMultipleFields() throws IOException {
        byte[] data = ParsingTestHelper.buildLyrics3v2Tag(
                "LYR", "Lyrics text",
                "INF", "Additional info",
                "AUT", "Author name"
        );
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.LYRICS3V2, raf, 0, data.length);

            assertTrue(ParsingTestHelper.hasField(metadata, "LYR"));
            assertTrue(ParsingTestHelper.hasField(metadata, "INF"));
            assertTrue(ParsingTestHelper.hasField(metadata, "AUT"));
        }
    }

    @Test
    @DisplayName("invalidStartMarker_v1 throws IOException when LYRICSBEGIN missing")
    void invalidStartMarker_v1_throwsIOException() throws IOException {
        byte[] data = "INVALIDBEGIN some lyrics LYRICSEND".getBytes();
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.LYRICS3V1, raf, 0, data.length));
        }
    }

    @Test
    @DisplayName("invalidEndMarker_v1 throws IOException when LYRICSEND missing")
    void invalidEndMarker_v1_throwsIOException() throws IOException {
        byte[] data = "LYRICSBEGIN some lyrics NOEND".getBytes();
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.LYRICS3V1, raf, 0, data.length));
        }
    }

    @Test
    @DisplayName("invalidStartMarker_v2 throws IOException when LYRICSBEGIN missing")
    void invalidStartMarker_v2_throwsIOException() throws IOException {
        byte[] data = "INVALIDBEGINLYR05hello000006LYRICS200".getBytes();
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.LYRICS3V2, raf, 0, data.length));
        }
    }

    @Test
    @DisplayName("invalidEndMarker_v2 throws IOException when LYRICS200 missing")
    void invalidEndMarker_v2_throwsIOException() throws IOException {
        byte[] data = "LYRICSBEGINLYR05hello000006NOVALID".getBytes();
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.LYRICS3V2, raf, 0, data.length));
        }
    }

    @Test
    @DisplayName("emptyLyrics_v1 handles empty content between markers")
    void emptyLyrics_v1() throws IOException {
        byte[] data = ParsingTestHelper.buildLyrics3v1Tag("");
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.LYRICS3V1, raf, 0, data.length);

            assertNotNull(metadata);
        }
    }
}