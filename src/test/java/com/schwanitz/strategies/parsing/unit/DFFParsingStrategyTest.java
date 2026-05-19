package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.DFFParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;
import java.nio.file.Path;

import static com.schwanitz.strategies.parsing.ParsingTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DFFParsingStrategyTest {

    private DFFParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new DFFParsingStrategy();
    }

    @Test
    @DisplayName("parseDIINChunk extracts DITI, DIAR, DIAL sub-chunks")
    void parseDIINChunk() throws IOException {
        byte[] data = buildDFFDIINChunk("DITI", "Test Title", "DIAR", "Test Artist", "DIAL", "Test Album");
        File file = writeTempFile(tempDir.toFile(), "test.dff", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.DFF_METADATA, source, 0, data.length);

            assertEquals("Test Title", ParsingTestHelper.findFieldValue(metadata, "Title"));
            assertEquals("Test Artist", ParsingTestHelper.findFieldValue(metadata, "Artist"));
            assertEquals("Test Album", ParsingTestHelper.findFieldValue(metadata, "Album"));
        }
    }

    @Test
    @DisplayName("parseTitleChunk extracts direct DITI chunk")
    void parseTitleChunk() throws IOException {
        byte[] titleValue = utf8("Direct Title");
        byte[] data = concat(ascii("DITI"), be64(titleValue.length), titleValue);
        File file = writeTempFile(tempDir.toFile(), "test.dff", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.DFF_METADATA, source, 0, data.length);

            assertEquals("Direct Title", ParsingTestHelper.findFieldValue(metadata, "Title"));
        }
    }

    @Test
    @DisplayName("unknownChunk is ignored with no fields added")
    void unknownChunk_ignored() throws IOException {
        byte[] unknownValue = utf8("unknown data");
        byte[] unknownSubChunk = concat(ascii("XXXX"), be64(unknownValue.length), unknownValue);
        byte[] diinData = concat(ascii("DIIN"), be64(unknownSubChunk.length), unknownSubChunk);
        byte[] data = diinData;

        File file = writeTempFile(tempDir.toFile(), "test.dff", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.DFF_METADATA, source, 0, data.length);

            assertNotNull(metadata);
            assertFalse(ParsingTestHelper.hasField(metadata, "XXXX"));
        }
    }

    @Test
    @DisplayName("emptyChunkData with size=0 does not crash")
    void emptyChunkData_noCrash() throws IOException {
        byte[] emptyDims = concat(ascii("DITI"), be64(0));
        byte[] diinData = concat(ascii("DIIN"), be64(emptyDims.length), emptyDims);
        File file = writeTempFile(tempDir.toFile(), "test.dff", diinData);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            assertDoesNotThrow(() -> {
                Metadata metadata = strategy.parseTag(TagFormat.DFF_METADATA, source, 0, diinData.length);
                assertNotNull(metadata);
            });
        }
    }
}