package com.schwanitz.api;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.strategies.parsing.ParsingTestHelper;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstests für die zentrale {@link MetadataManager}-API.
 * <p>
 * Diese Tests überprüfen die komplette Pipeline von Tag-Erkennung
 * und Tag-Parsing über verschiedene Eingabequellen hinweg.
 * </p>
 */
@DisplayName("MetadataManager Integration Tests")
class MetadataManagerTest {

    private MetadataManager manager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        manager = new MetadataManager();
    }

    // ================================
    // readFromFile Tests
    // ================================

    @Test
    @DisplayName("readFromFile detects and parses ID3v2.3 tag")
    void readFromFileWithID3v2() throws IOException {
        byte[] tagData = buildID3v2_3Tag("Test Title", "Test Artist");
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test.mp3", tagData);

        List<Metadata> metadata = manager.readFromFile(file.getAbsolutePath());

        assertFalse(metadata.isEmpty(), "Should detect at least one tag");
        assertEquals("ID3v2.3", metadata.get(0).getTagFormat());
        assertEquals("Test Title", ParsingTestHelper.findFieldValue(metadata.get(0), "TIT2"));
        assertEquals("Test Artist", ParsingTestHelper.findFieldValue(metadata.get(0), "TPE1"));
    }

    @Test
    @DisplayName("readFromFile detects and parses ID3v1.1 tag")
    void readFromFileWithID3v1_1() throws IOException {
        byte[] tagData = ParsingTestHelper.buildID3v1Tag("My Title", "My Artist", "My Album", "2024", "Comment", (byte) 5, (byte) 0);
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test_v1.mp3", tagData);

        List<Metadata> metadata = manager.readFromFile(file.getAbsolutePath());

        assertFalse(metadata.isEmpty(), "Should detect ID3v1.1 tag");
        assertEquals("ID3v1.1", metadata.get(0).getTagFormat());
        assertEquals("My Title", ParsingTestHelper.findFieldValue(metadata.get(0), "TIT2"));
        assertEquals("My Artist", ParsingTestHelper.findFieldValue(metadata.get(0), "TPE1"));
    }

    @Test
    @DisplayName("readFromFile detects multiple tags in same file")
    void readFromFileWithMultipleTags() throws IOException {
        byte[] id3v2 = buildID3v2_3Tag("ID3v2 Title", "ID3v2 Artist");
        byte[] id3v1 = ParsingTestHelper.buildID3v1Tag("ID3v1 Title", "ID3v1 Artist", "Album", "2024", "", (byte) 0, (byte) 0);
        byte[] combined = ParsingTestHelper.concat(id3v2, id3v1);

        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test_multi.mp3", combined);

        List<Metadata> metadata = manager.readFromFile(file.getAbsolutePath());

        assertEquals(2, metadata.size(), "Should detect both ID3v2 and ID3v1");

        var formats = metadata.stream().map(Metadata::getTagFormat).toList();
        assertTrue(formats.contains("ID3v2.3"));
        assertTrue(formats.contains("ID3v1"));
    }

    @Test
    @DisplayName("readFromFile with no tags returns empty list")
    void readFromFileNoTags() throws IOException {
        byte[] dummyData = new byte[256];
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "empty.mp3", dummyData);

        List<Metadata> metadata = manager.readFromFile(file.getAbsolutePath());

        assertTrue(metadata.isEmpty(), "Should return empty list for file without tags");
    }

    @Test
    @DisplayName("readFromFile with ScanConfiguration fullScan")
    void readFromFileWithFullScanConfig() throws IOException {
        byte[] tagData = buildID3v2_3Tag("FullScan Title", "FullScan Artist");
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test_full.mp3", tagData);

        List<Metadata> metadata = manager.readFromFile(file.getAbsolutePath(), ScanConfiguration.fullScan());

        assertFalse(metadata.isEmpty());
        assertEquals("ID3v2.3", metadata.get(0).getTagFormat());
    }

    @Test
    @DisplayName("readFromFile with custom scan for specific format")
    void readFromFileWithCustomScan() throws IOException {
        byte[] id3v2 = buildID3v2_3Tag("Title", "Artist");
        byte[] id3v1 = ParsingTestHelper.buildID3v1Tag("V1Title", "V1Artist", "Album", "2024", "", (byte) 0, (byte) 0);
        byte[] combined = ParsingTestHelper.concat(id3v2, id3v1);

        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "test_custom.mp3", combined);

        // Custom scan only for ID3v2_3
        List<Metadata> metadata = manager.readFromFile(file.getAbsolutePath(), ScanConfiguration.customScan(TagFormat.ID3V2_3));

        assertEquals(1, metadata.size(), "Custom scan should only find ID3v2.3");
        assertEquals("ID3v2.3", metadata.get(0).getTagFormat());
    }

    // ================================
    // readFromFiles (Batch) Tests
    // ================================

    @Test
    @DisplayName("readFromFiles processes multiple files")
    void readFromFilesBatch() {
        List<String> filePaths = List.of(
                writeTempMp3("batch1.mp3", buildID3v2_3Tag("Title1", "Artist1")),
                writeTempMp3("batch2.mp3", buildID3v2_3Tag("Title2", "Artist2")),
                writeTempMp3("batch3.mp3", buildID3v2_3Tag("Title3", "Artist3"))
        );

        Map<String, List<Metadata>> results = manager.readFromFiles(filePaths);

        assertEquals(3, results.size());
        for (String path : filePaths) {
            assertTrue(results.containsKey(path), "Result should contain " + path);
            assertFalse(results.get(path).isEmpty(), "Should detect tags in " + path);
        }
    }

    @Test
    @DisplayName("readFromFiles handles empty list")
    void readFromFilesEmptyList() {
        Map<String, List<Metadata>> results = manager.readFromFiles(List.of());

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("readFromFiles filters null entries")
    void readFromFilesWithNullEntries() {
        String validPath = writeTempMp3("valid.mp3", buildID3v2_3Tag("Valid", "Artist"));
        List<String> filePaths = Arrays.asList(null, validPath, null);

        Map<String, List<Metadata>> results = manager.readFromFiles(filePaths);

        assertEquals(1, results.size());
        assertTrue(results.containsKey(validPath));
    }

    @Test
    @DisplayName("readFromFiles handles non-existent files gracefully")
    void readFromFilesWithMissingFile() {
        String existingPath = writeTempMp3("exists.mp3", buildID3v2_3Tag("Exists", "Artist"));
        List<String> filePaths = List.of(existingPath, "/nonexistent/file.mp3");

        Map<String, List<Metadata>> results = manager.readFromFiles(filePaths);

        assertEquals(2, results.size());
        assertFalse(results.get(existingPath).isEmpty());
        assertTrue(results.get("/nonexistent/file.mp3").isEmpty(), "Missing file should yield empty metadata");
    }

    // ================================
    // Null Parameter Tests
    // ================================

    @Nested
    @DisplayName("Null parameter validation")
    class NullParameters {

        @Test
        @DisplayName("readFromFile(String) throws NullPointerException for null path")
        void nullPathString() {
            assertThrows(NullPointerException.class, () -> manager.readFromFile((String) null));
        }

        @Test
        @DisplayName("readFromFile(String, ScanConfiguration) throws NullPointerException for null path")
        void nullPathStringWithConfig() {
            assertThrows(NullPointerException.class, () -> manager.readFromFile((String) null, ScanConfiguration.fullScan()));
        }

        @Test
        @DisplayName("readFromFile(String, ScanConfiguration) throws NullPointerException for null config")
        void nullConfig() throws IOException {
            assertThrows(NullPointerException.class, () -> manager.readFromFile("/tmp/test.mp3", (ScanConfiguration) null));
        }

        @Test
        @DisplayName("readFromFiles(List) throws NullPointerException for null list")
        void nullList() {
            assertThrows(NullPointerException.class, () -> manager.readFromFiles(null));
        }

        @Test
        @DisplayName("readFromFiles(List, ScanConfiguration) throws NullPointerException for null config")
        void nullListConfig() {
            assertThrows(NullPointerException.class, () -> manager.readFromFiles(List.of(), null));
        }
    }

    // ================================
    // Convenience method tests
    // ================================

    @Test
    @DisplayName("readFromFile(Path) works with NIO Path")
    void readFromFileWithNioPath() throws IOException {
        byte[] tagData = buildID3v2_3Tag("Path Title", "Path Artist");
        File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), "nio.mp3", tagData);

        List<Metadata> metadata = manager.readFromFile(file.toPath());

        assertFalse(metadata.isEmpty());
        assertEquals("ID3v2.3", metadata.get(0).getTagFormat());
    }

    // ================================
    // Helper Methods
    // ================================

    private byte[] buildID3v2_3Tag(String title, String artist) {
        byte[] tit2 = ParsingTestHelper.buildID3v2TextFrame(3, "TIT2", 0, title);
        byte[] tpe1 = ParsingTestHelper.buildID3v2TextFrame(3, "TPE1", 0, artist);
        byte[] frames = ParsingTestHelper.concat(tit2, tpe1);
        byte[] header = ParsingTestHelper.buildID3v2Header(3, 0, frames.length);
        return ParsingTestHelper.concat(header, frames);
    }

    private String writeTempMp3(String name, byte[] tagData) {
        try {
            File file = ParsingTestHelper.writeTempFile(tempDir.toFile(), name, tagData);
            return file.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
