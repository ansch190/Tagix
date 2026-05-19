package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.BWFParsingStrategy;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static com.schwanitz.strategies.parsing.ParsingTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BWFParsingStrategyTest {

    private BWFParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new BWFParsingStrategy();
    }

    @Test
    @DisplayName("parseBWFV0 extracts description, originator, originatorRef, date, time, timeReference, version=0")
    void parseBWFV0() throws IOException {
        byte[] data = buildBWFChunk("Test Description", "Test Originator", "REF001",
                "2024-01-15", "14:30:00", 48000L, 0, null, null);
        File file = writeTempFile(tempDir.toFile(), "test.bwf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.BWF_V0, source, 0, data.length);

            assertEquals("Test Description", ParsingTestHelper.findFieldValue(metadata, "Description"));
            assertEquals("Test Originator", ParsingTestHelper.findFieldValue(metadata, "Originator"));
            assertEquals("REF001", ParsingTestHelper.findFieldValue(metadata, "OriginatorReference"));
            assertEquals("2024-01-15", ParsingTestHelper.findFieldValue(metadata, "OriginationDate"));
            assertEquals("14:30:00", ParsingTestHelper.findFieldValue(metadata, "OriginationTime"));
            assertEquals("48000", ParsingTestHelper.findFieldValue(metadata, "TimeReference"));
            assertEquals("0", ParsingTestHelper.findFieldValue(metadata, "Version"));
        }
    }

    @Test
    @DisplayName("parseBWFV1 extracts same as V0 with version=1")
    void parseBWFV1() throws IOException {
        byte[] data = buildBWFChunk("V1 Description", "V1 Originator", "REF002",
                "2024-06-20", "09:15:00", 96000L, 1, null, null);
        File file = writeTempFile(tempDir.toFile(), "test.bwf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.BWF_V1, source, 0, data.length);

            assertEquals("V1 Description", ParsingTestHelper.findFieldValue(metadata, "Description"));
            assertEquals("1", ParsingTestHelper.findFieldValue(metadata, "Version"));
        }
    }

    @Test
    @DisplayName("parseBWFV2 includes loudness values")
    void parseBWFV2() throws IOException {
        byte[] loudnessData = new byte[180];
        int offset = 0;
        loudnessData[offset++] = (byte) 0xE0; loudnessData[offset++] = (byte) 0x0E;
        loudnessData[offset++] = (byte) 0x00; loudnessData[offset++] = (byte) 0x32;
        offset += 2;
        loudnessData[offset++] = (byte) 0xFF; loudnessData[offset++] = (byte) 0x9C;
        offset += 2;

        byte[] data = buildBWFChunk("V2 Track", "Studio", "REF003",
                "2024-03-10", "08:00:00", 0L, 2, null, loudnessData);
        File file = writeTempFile(tempDir.toFile(), "test.bwf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.BWF_V2, source, 0, data.length);

            assertTrue(ParsingTestHelper.hasField(metadata, "LoudnessValue"));
            assertTrue(ParsingTestHelper.hasField(metadata, "LoudnessRange"));
            assertTrue(ParsingTestHelper.hasField(metadata, "MaxTruePeakLevel"));
            assertTrue(ParsingTestHelper.hasField(metadata, "MaxMomentaryLoudness"));
            assertTrue(ParsingTestHelper.hasField(metadata, "MaxShortTermLoudness"));
            assertEquals("2", ParsingTestHelper.findFieldValue(metadata, "Version"));
        }
    }

    @Test
    @DisplayName("emptyDescription omits empty string fields")
    void emptyDescription_omitsFields() throws IOException {
        byte[] data = buildBWFChunk("", "", "",
                "", "", 0L, 0, null, null);
        File file = writeTempFile(tempDir.toFile(), "test.bwf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.BWF_V0, source, 0, data.length);

            assertNull(ParsingTestHelper.findFieldValue(metadata, "Description"));
            assertNull(ParsingTestHelper.findFieldValue(metadata, "Originator"));
            assertNull(ParsingTestHelper.findFieldValue(metadata, "OriginatorReference"));
        }
    }

    @Test
    @DisplayName("invalidChunkId throws IOException when not bext")
    void invalidChunkId_throwsIOException() throws IOException {
        byte[] data = concat(ascii("xxxx"), le32(602), new byte[602]);
        File file = writeTempFile(tempDir.toFile(), "test.bwf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.BWF_V0, source, 0, data.length));
        }
    }

    @Test
    @DisplayName("tooSmallChunk throws IOException when chunkSize < 602")
    void tooSmallChunk_throwsIOException() throws IOException {
        byte[] data = concat(ascii("bext"), le32(100), new byte[100]);
        File file = writeTempFile(tempDir.toFile(), "test.bwf", data);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.BWF_V0, source, 0, data.length));
        }
    }
}