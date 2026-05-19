package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.APEParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("APEParsingStrategy Unit Tests")
class APEParsingStrategyTest {

    private APEParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new APEParsingStrategy();
    }

    private Metadata parseAPE(APEParsingStrategy strategy, byte[] tagData) throws IOException {
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.ape", tagData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            return strategy.parseTag(TagFormat.APEV2, source, 0, tagData.length);
        }
    }

    private Metadata parseAPE(APEParsingStrategy strategy, byte[] tagData, long offset, long size) throws IOException {
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.ape", tagData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            return strategy.parseTag(TagFormat.APEV2, source, offset, size);
        }
    }

    @Test
    @DisplayName("Parse APEv2 tag with single Title field")
    void parseAPEv2Basic() throws IOException {
        byte[] tagData = ParsingTestHelper.buildAPETag(2000, true,
                new ParsingTestHelper.APEItem("Title", "Test Title"));

        Metadata metadata = parseAPE(strategy, tagData);

        assertNotNull(metadata);
        assertEquals(ParsingTestHelper.findFieldValue(metadata, "Title"), "Test Title");
    }

    @Test
    @DisplayName("Parse APEv2 tag with multiple fields")
    void parseAPEv2MultipleFields() throws IOException {
        byte[] tagData = ParsingTestHelper.buildAPETag(2000, true,
                new ParsingTestHelper.APEItem("Title", "My Song"),
                new ParsingTestHelper.APEItem("Artist", "My Artist"),
                new ParsingTestHelper.APEItem("Album", "My Album"));

        Metadata metadata = parseAPE(strategy, tagData);

        assertNotNull(metadata);
        assertEquals("My Song", ParsingTestHelper.findFieldValue(metadata, "Title"));
        assertEquals("My Artist", ParsingTestHelper.findFieldValue(metadata, "Artist"));
        assertEquals("My Album", ParsingTestHelper.findFieldValue(metadata, "Album"));
    }

    @Test
    @DisplayName("Parse APEv1 tag (version 1000)")
    void parseAPEv1Tag() throws IOException {
        byte[] tagData = ParsingTestHelper.buildAPETag(1000, true,
                new ParsingTestHelper.APEItem("Title", "APEv1 Title"),
                new ParsingTestHelper.APEItem("Artist", "APEv1 Artist"));

        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.ape1", tagData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.APEV1, source, 0, tagData.length);
            assertNotNull(metadata);
            assertEquals("APEv1 Title", ParsingTestHelper.findFieldValue(metadata, "Title"));
            assertEquals("APEv1 Artist", ParsingTestHelper.findFieldValue(metadata, "Artist"));
        }
    }

    @Test
    @DisplayName("Parse APEv2 tag with header present")
    void parseAPEv2WithHeader() throws IOException {
        byte[] tagData = ParsingTestHelper.buildAPETag(2000, true,
                new ParsingTestHelper.APEItem("Title", "With Header"),
                new ParsingTestHelper.APEItem("Artist", "Header Artist"));

        Metadata metadata = parseAPE(strategy, tagData);

        assertNotNull(metadata);
        assertEquals("With Header", ParsingTestHelper.findFieldValue(metadata, "Title"));
        assertEquals("Header Artist", ParsingTestHelper.findFieldValue(metadata, "Artist"));
    }

    @Test
    @DisplayName("Throw IOException on invalid preamble")
    void parseInvalidPreamble() throws IOException {
        byte[] badData = new byte[64];
        java.util.Arrays.fill(badData, (byte) 0xFF);

        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "bad.ape", badData);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.APEV2, source, 0, badData.length));
        }
    }

    @Test
    @DisplayName("Parse APEv2 tag with zero items produces empty metadata")
    void parseEmptyTag() throws IOException {
        byte[] tagData = ParsingTestHelper.buildAPETag(2000, true);

        Metadata metadata = parseAPE(strategy, tagData);

        assertNotNull(metadata);
        assertTrue(metadata.getFields().isEmpty());
    }
}