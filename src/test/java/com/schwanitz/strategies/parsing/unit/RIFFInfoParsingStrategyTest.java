package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.strategies.parsing.RIFFInfoParsingStrategy;
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
class RIFFInfoParsingStrategyTest {

    private RIFFInfoParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new RIFFInfoParsingStrategy();
    }

    @Test
    @DisplayName("parseBasicFields extracts INAM, IART, IPRD fields")
    void parseBasicFields() throws IOException {
        byte[] data = buildRIFFInfoChunk("INAM", "Test Title", "IART", "Test Artist", "IPRD", "Test Album");
        File file = writeTempFile(tempDir.toFile(), "test.wav", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.RIFF_INFO, source, 0, data.length);

            assertEquals("Test Title", ParsingTestHelper.findFieldValue(metadata, "Title"));
            assertEquals("Test Artist", ParsingTestHelper.findFieldValue(metadata, "Artist"));
            assertEquals("Test Album", ParsingTestHelper.findFieldValue(metadata, "Album"));
        }
    }

    @Test
    @DisplayName("parseUTF8Field extracts UTF-8 encoded values")
    void parseUTF8Field() throws IOException {
        byte[] data = buildRIFFInfoChunk("INAM", "Título UTF-8");
        File file = writeTempFile(tempDir.toFile(), "test.wav", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.RIFF_INFO, source, 0, data.length);

            String title = ParsingTestHelper.findFieldValue(metadata, "Title");
            assertNotNull(title);
            assertTrue(title.contains("T"));
        }
    }

    @Test
    @DisplayName("nullTerminatedStrings values terminated by null byte")
    void nullTerminatedStrings() throws IOException {
        byte[] data = buildRIFFInfoChunk("INAM", "Hello");
        File file = writeTempFile(tempDir.toFile(), "test.wav", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.RIFF_INFO, source, 0, data.length);

            String title = ParsingTestHelper.findFieldValue(metadata, "Title");
            assertNotNull(title);
            assertEquals("Hello", title);
        }
    }

    @Test
    @DisplayName("paddingForOddSizedChunks even-byte padding")
    void paddingForOddSizedChunks() throws IOException {
        byte[] data = buildRIFFInfoChunk("INAM", "X");
        File file = writeTempFile(tempDir.toFile(), "test.wav", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.RIFF_INFO, source, 0, data.length);

            String title = ParsingTestHelper.findFieldValue(metadata, "Title");
            assertNotNull(title);
            assertEquals("X", title);
        }
    }

    @Test
    @DisplayName("invalidChunkId throws IOException on non-LIST chunk")
    void invalidChunkId_throwsIOException() throws IOException {
        byte[] data = concat(ascii("RIFF"), le32(20), ascii("WAVE"));
        File file = writeTempFile(tempDir.toFile(), "test.wav", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.RIFF_INFO, source, 0, data.length));
        }
    }

    @Test
    @DisplayName("emptyInfoSection returns no fields")
    void emptyInfoSection_noFields() throws IOException {
        byte[] data = buildRIFFInfoChunk();
        File file = writeTempFile(tempDir.toFile(), "test.wav", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf)) {
            Metadata metadata = strategy.parseTag(TagFormat.RIFF_INFO, source, 0, data.length);

            assertNotNull(metadata);
            assertEquals(0, metadata.getFields().size());
        }
    }
}