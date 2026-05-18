package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.DSFParsingStrategy;
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
class DSFParsingStrategyTest {

    private DSFParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new DSFParsingStrategy();
    }

    @Test
    @DisplayName("canHandle returns true for DSF_METADATA")
    void canHandle_returnsTrueForDSF_METADATA() {
        assertTrue(strategy.canHandle(TagFormat.DSF_METADATA));
    }

    @Test
    @DisplayName("canHandle returns false for other formats")
    void canHandle_returnsFalseForOtherFormats() {
        assertFalse(strategy.canHandle(TagFormat.ID3V2_3));
        assertFalse(strategy.canHandle(TagFormat.MP4));
        assertFalse(strategy.canHandle(TagFormat.DFF_METADATA));
        assertFalse(strategy.canHandle(TagFormat.WAVPACK_NATIVE));
    }

    @Test
    @DisplayName("parseID3v2.3 chunk returns metadata with version info")
    void parseID3v2_3Chunk_returnsMetadataWithVersionInfo() throws IOException {
        byte[] id3Header = buildID3v2Header(3, 0, 100);
        byte[] id3Data = concat(id3Header, pad(100));
        long chunkSize = 12 + id3Data.length;
        byte[] data = concat(ascii("ID3 "), le64(chunkSize), id3Data);
        File file = writeTempFile(tempDir.toFile(), "test.dsf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.DSF_METADATA, raf, 0, data.length);

            String fieldValue = ParsingTestHelper.findFieldValue(metadata, "DSF_ID3");
            assertNotNull(fieldValue);
            assertTrue(fieldValue.contains("ID3v2.3"));
        }
    }

    @Test
    @DisplayName("parseID3v2.4 chunk returns metadata with version info")
    void parseID3v2_4Chunk_returnsMetadataWithVersionInfo() throws IOException {
        byte[] id3Header = buildID3v2Header(4, 0, 100);
        byte[] id3Data = concat(id3Header, pad(100));
        long chunkSize = 12 + id3Data.length;
        byte[] data = concat(ascii("ID3 "), le64(chunkSize), id3Data);
        File file = writeTempFile(tempDir.toFile(), "test.dsf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.DSF_METADATA, raf, 0, data.length);

            String fieldValue = ParsingTestHelper.findFieldValue(metadata, "DSF_ID3");
            assertNotNull(fieldValue);
            assertTrue(fieldValue.contains("ID3v2.4"));
        }
    }

    @Test
    @DisplayName("parseInvalidChunkId returns empty metadata")
    void parseInvalidChunkId_returnsEmptyMetadata() throws IOException {
        byte[] data = concat(ascii("XXXX"), le64(100), pad(100));
        File file = writeTempFile(tempDir.toFile(), "test.dsf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.DSF_METADATA, raf, 0, data.length);

            assertNotNull(metadata);
            assertEquals(0, metadata.getFields().size());
        }
    }

    @Test
    @DisplayName("parseInvalidID3Data returns empty metadata")
    void parseInvalidID3Data_returnsEmptyMetadata() throws IOException {
        byte[] chunkHeader = concat(ascii("ID3 "), le64(12 + 50));
        byte[] invalidData = pad(50);
        byte[] data = concat(chunkHeader, invalidData);
        File file = writeTempFile(tempDir.toFile(), "test.dsf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.DSF_METADATA, raf, 0, data.length);

            assertNotNull(metadata);
            assertEquals(0, metadata.getFields().size());
        }
    }
}
