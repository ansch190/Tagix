package com.schwanitz.strategies.parsing.unit;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.MP4ParsingStrategy;
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
class MP4ParsingStrategyTest {

    private MP4ParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new MP4ParsingStrategy();
    }

    @Test
    @DisplayName("parseUTF8TextAtom extracts text from data type 1")
    void parseUTF8TextAtom() throws IOException {
        byte[] ilstData = concat(
                buildMP4MetadataAtom("©nam", 1, utf8("Test Title"))
        );
        byte[] fileData = buildMP4FullFile(ilstData);
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp4", fileData);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.MP4, source, 0, file.length());

            String title = ParsingTestHelper.findFieldValue(metadata, "Title");
            assertNotNull(title);
            assertEquals("Test Title", title);
        }
    }

    @Test
    @DisplayName("parseIntegerAtom extracts track numbers from data type 0")
    void parseIntegerAtom() throws IOException {
        byte[] trackData = new byte[]{0, 0, 0, 3, 0, 12};
        byte[] ilstData = concat(
                buildMP4MetadataAtom("trkn", 0, trackData)
        );
        byte[] fileData = buildMP4FullFile(ilstData);
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp4", fileData);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.MP4, source, 0, file.length());

            String track = ParsingTestHelper.findFieldValue(metadata, "Track");
            assertNotNull(track);
            assertEquals("3/12", track);
        }
    }

    @Test
    @DisplayName("parseBoolAtom extracts boolean from data type 0 for cpil/pcst")
    void parseBoolAtom() throws IOException {
        byte[] trueData = new byte[]{1};
        byte[] falseData = new byte[]{0};
        byte[] ilstData = concat(
                buildMP4MetadataAtom("cpil", 0, trueData),
                buildMP4MetadataAtom("pcst", 0, falseData)
        );
        byte[] fileData = buildMP4FullFile(ilstData);
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp4", fileData);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.MP4, source, 0, file.length());

            assertEquals("true", ParsingTestHelper.findFieldValue(metadata, "Compilation"));
            assertEquals("false", ParsingTestHelper.findFieldValue(metadata, "Podcast_Flag"));
        }
    }

    @Test
    @DisplayName("parseTrackDiscNumber extracts track/disc from trkn/disk special format")
    void parseTrackDiscNumber() throws IOException {
        byte[] trackData = new byte[]{0, 0, 0, 5, 0, 10};
        byte[] discData = new byte[]{0, 0, 0, 2, 0, 3};
        byte[] ilstData = concat(
                buildMP4MetadataAtom("trkn", 0, trackData),
                buildMP4MetadataAtom("disk", 0, discData)
        );
        byte[] fileData = buildMP4FullFile(ilstData);
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp4", fileData);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.MP4, source, 0, file.length());

            assertEquals("5/10", ParsingTestHelper.findFieldValue(metadata, "Track"));
            assertEquals("2/3", ParsingTestHelper.findFieldValue(metadata, "Disc"));
        }
    }

    @Test
    @DisplayName("noMoovAtom throws IOException")
    void noMoovAtom_throwsIOException() throws IOException {
        byte[] fileData = buildMP4Atom("free", new byte[100]);
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp4", fileData);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            assertThrows(IOException.class, () ->
                    strategy.parseTag(TagFormat.MP4, source, 0, file.length()));
        }
    }

    @Test
    @DisplayName("noUdtaAtom returns empty metadata")
    void noUdtaAtom_returnsEmptyMetadata() throws IOException {
        byte[] moovData = buildContainer("moov", buildContainer("mvhd", new byte[100]));
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp4", moovData);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            SeekableDataSource source = SeekableDataSources.forRandomAccessFile(raf);
            Metadata metadata = strategy.parseTag(TagFormat.MP4, source, 0, file.length());

            assertNotNull(metadata);
            assertTrue(metadata.getFields().isEmpty());
        }
    }
}