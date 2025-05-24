package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.tagging.TagFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@Tag("ape")
@Tag("parsing")
@DisplayName("APEParsingStrategy Comprehensive Test Suite")
class APEParsingStrategyTest {

    private APEParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new APEParsingStrategy();
    }

    // ==================== Basic APE Tag Tests ====================

    @Test
    @DisplayName("Parse valid APEv2 tag with standard fields")
    void testParseAPEv2StandardFields() throws IOException {
        File testFile = createAPEv2File(Arrays.asList(
                new APEItem("Title", "Test Title"),
                new APEItem("Artist", "Test Artist"),
                new APEItem("Album", "Test Album"),
                new APEItem("Year", "2024"),
                new APEItem("Genre", "Rock"),
                new APEItem("Track", "5/12"),
                new APEItem("Comment", "Test Comment")
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            assertNotNull(metadata);
            assertEquals("APEv2", metadata.getTagFormat());

            assertFieldValue(metadata, "Title", "Test Title");
            assertFieldValue(metadata, "Artist", "Test Artist");
            assertFieldValue(metadata, "Album", "Test Album");
            assertFieldValue(metadata, "Year", "2024");
            assertFieldValue(metadata, "Genre", "Rock");
            assertFieldValue(metadata, "Track", "5/12");
            assertFieldValue(metadata, "Comment", "Test Comment");
        }
    }

    @Test
    @DisplayName("Parse APEv1 tag (version 1000)")
    void testParseAPEv1() throws IOException {
        File testFile = createAPEFile(1000, Arrays.asList(
                new APEItem("Title", "APEv1 Title"),
                new APEItem("Artist", "APEv1 Artist")
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV1, raf,
                    testFile.length() - tagSize, tagSize);

            assertNotNull(metadata);
            assertEquals("APEv1", metadata.getTagFormat());
            assertFieldValue(metadata, "Title", "APEv1 Title");
            assertFieldValue(metadata, "Artist", "APEv1 Artist");
        }
    }

    // ==================== Multi-Value Tests ====================

    @Test
    @DisplayName("Parse APE tag with multi-value fields")
    void testParseMultiValueFields() throws IOException {
        // Create multi-value field with null separators
        byte[] multiArtist = createMultiValueData("Artist 1", "Artist 2", "Artist 3");
        byte[] multiGenre = createMultiValueData("Rock", "Alternative", "Indie");

        File testFile = createAPEv2FileWithRawData(Arrays.asList(
                new APEItemRaw("Artist", multiArtist, APEItemType.UTF8),
                new APEItemRaw("Genre", multiGenre, APEItemType.UTF8)
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            assertFieldValue(metadata, "Artist", "Artist 1; Artist 2; Artist 3");
            assertFieldValue(metadata, "Genre", "Rock; Alternative; Indie");
        }
    }

    // ==================== Binary Item Tests ====================

    @Test
    @DisplayName("Parse APE tag with binary cover art (JPEG)")
    void testParseBinaryCoverArtJPEG() throws IOException {
        byte[] jpegData = createMockJPEGData();

        File testFile = createAPEv2FileWithRawData(Arrays.asList(
                new APEItemRaw("Cover Art (Front)", jpegData, APEItemType.BINARY),
                new APEItemRaw("Title", "Album with Cover".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8)
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            MetadataField<?> coverField = findField(metadata, "Cover Art (Front)");
            assertNotNull(coverField);
            String value = coverField.getValue().toString();
            assertTrue(value.startsWith("[IMAGE:JPEG,"));
            assertTrue(value.contains("preview:"));
            assertTrue(value.contains(String.valueOf(jpegData.length) + " bytes"));
        }
    }

    @Test
    @DisplayName("Parse APE tag with different image formats")
    @ParameterizedTest(name = "Format: {0}")
    @CsvSource({
            "PNG, 0x89, 0x50, 0x4E, 0x47",
            "GIF, 0x47, 0x49, 0x46, 0x38",
            "BMP, 0x42, 0x4D, 0x00, 0x00"
    })
    void testParseDifferentImageFormats(String format, int b1, int b2, int b3, int b4) throws IOException {
        byte[] imageData = new byte[100];
        imageData[0] = (byte)b1;
        imageData[1] = (byte)b2;
        imageData[2] = (byte)b3;
        imageData[3] = (byte)b4;

        File testFile = createAPEv2FileWithRawData(Arrays.asList(
                new APEItemRaw("Cover", imageData, APEItemType.BINARY)
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            MetadataField<?> coverField = findField(metadata, "Cover");
            assertNotNull(coverField);
            assertTrue(coverField.getValue().toString().contains(format));
        }
    }

    // ==================== Key Normalization Tests ====================

    @Test
    @DisplayName("Test key normalization for common variations")
    void testKeyNormalization() throws IOException {
        File testFile = createAPEv2File(Arrays.asList(
                new APEItem("album artist", "Test Album Artist"),
                new APEItem("ALBUMARTIST", "Another Album Artist"),
                new APEItem("AlbumArtist", "Correct Album Artist"),
                new APEItem("replaygain_track_gain", "-6.5 dB"),
                new APEItem("REPLAYGAIN_TRACK_GAIN", "-7.0 dB")
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            // Should normalize to same key
            int albumArtistCount = 0;
            int replayGainCount = 0;

            for (MetadataField<?> field : metadata.getFields()) {
                if (field.getKey().equals("AlbumArtist")) albumArtistCount++;
                if (field.getKey().equals("REPLAYGAIN_TRACK_GAIN")) replayGainCount++;
            }

            // Due to normalization, we should have one of each
            assertEquals(1, albumArtistCount, "AlbumArtist should be normalized to one field");
            assertEquals(1, replayGainCount, "ReplayGain should be normalized to one field");
        }
    }

    // ==================== Special Characters and Encoding Tests ====================

    @Test
    @DisplayName("Parse APE tag with UTF-8 special characters")
    void testParseUTF8SpecialCharacters() throws IOException {
        File testFile = createAPEv2File(Arrays.asList(
                new APEItem("Title", "Título con ñ y acentos"),
                new APEItem("Artist", "Björk"),
                new APEItem("Album", "Album mit Ümläüten"),
                new APEItem("Comment", "Комментарий на русском"),
                new APEItem("Genre", "日本語ジャンル")
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            assertFieldValue(metadata, "Title", "Título con ñ y acentos");
            assertFieldValue(metadata, "Artist", "Björk");
            assertFieldValue(metadata, "Album", "Album mit Ümläüten");
            assertFieldValue(metadata, "Comment", "Комментарий на русском");
            assertFieldValue(metadata, "Genre", "日本語ジャンル");
        }
    }

    // ==================== Extended Metadata Tests ====================

    @Test
    @DisplayName("Parse APE tag with extended metadata fields")
    void testParseExtendedMetadata() throws IOException {
        File testFile = createAPEv2File(Arrays.asList(
                new APEItem("MUSICBRAINZ_TRACKID", "12345678-1234-1234-1234-123456789012"),
                new APEItem("MUSICBRAINZ_ALBUMID", "87654321-4321-4321-4321-210987654321"),
                new APEItem("REPLAYGAIN_ALBUM_GAIN", "-8.23 dB"),
                new APEItem("REPLAYGAIN_ALBUM_PEAK", "0.988751"),
                new APEItem("BPM", "128"),
                new APEItem("InitialKey", "Am"),
                new APEItem("Mood", "Energetic"),
                new APEItem("CatalogNumber", "CAT-12345"),
                new APEItem("ISRC", "USRC17607839"),
                new APEItem("Barcode", "0123456789012")
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            // Verify all extended fields
            assertFieldValue(metadata, "MUSICBRAINZ_TRACKID", "12345678-1234-1234-1234-123456789012");
            assertFieldValue(metadata, "MUSICBRAINZ_ALBUMID", "87654321-4321-4321-4321-210987654321");
            assertFieldValue(metadata, "REPLAYGAIN_ALBUM_GAIN", "-8.23 dB");
            assertFieldValue(metadata, "REPLAYGAIN_ALBUM_PEAK", "0.988751");
            assertFieldValue(metadata, "BPM", "128");
            assertFieldValue(metadata, "InitialKey", "Am");
            assertFieldValue(metadata, "Mood", "Energetic");
            assertFieldValue(metadata, "CatalogNumber", "CAT-12345");
            assertFieldValue(metadata, "ISRC", "USRC17607839");
            assertFieldValue(metadata, "Barcode", "0123456789012");
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Handle corrupted APE tag with invalid preamble")
    void testCorruptedAPEInvalidPreamble() throws IOException {
        File testFile = createCorruptedAPEFile(CorruptionType.INVALID_PREAMBLE);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            assertThrows(IOException.class, () -> {
                strategy.parseTag(TagFormat.APEV2, raf, testFile.length() - 32, 32);
            });
        }
    }

    @Test
    @DisplayName("Handle APE tag with invalid version")
    void testAPEInvalidVersion() throws IOException {
        File testFile = createAPEFile(3000, Arrays.asList()); // Invalid version

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            assertThrows(IOException.class, () -> {
                strategy.parseTag(TagFormat.APEV2, raf, testFile.length() - 32, 32);
            });
        }
    }

    @Test
    @DisplayName("Handle APE tag with invalid item count")
    void testAPEInvalidItemCount() throws IOException {
        File testFile = createCorruptedAPEFile(CorruptionType.INVALID_ITEM_COUNT);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            assertThrows(IOException.class, () -> {
                strategy.parseTag(TagFormat.APEV2, raf, testFile.length() - 64, 64);
            });
        }
    }

    @Test
    @DisplayName("Handle APE item with invalid key")
    void testAPEInvalidItemKey() throws IOException {
        // Test with reserved keys
        File testFile = createAPEv2File(Arrays.asList(
                new APEItem("ID3", "Should be ignored"),    // Reserved
                new APEItem("TAG", "Should be ignored"),    // Reserved
                new APEItem("OggS", "Should be ignored"),   // Reserved
                new APEItem("Title", "Valid Title")         // Valid
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            // Reserved keys should be skipped
            assertNull(findField(metadata, "ID3"));
            assertNull(findField(metadata, "TAG"));
            assertNull(findField(metadata, "OggS"));
            assertFieldValue(metadata, "Title", "Valid Title");
        }
    }

    @Test
    @DisplayName("Handle APE item with key containing invalid characters")
    void testAPEInvalidKeyCharacters() throws IOException {
        // Keys with invalid characters should be rejected
        List<String> invalidKeys = Arrays.asList(
                "Key=Value",     // Contains =
                "Key[Test]",     // Contains [ ]
                "Key\0Test",     // Contains null
                "Key\nNewline"   // Contains control char
        );

        for (String invalidKey : invalidKeys) {
            File testFile = createAPEv2File(Arrays.asList(
                    new APEItem(invalidKey, "Value"),
                    new APEItem("ValidKey", "ValidValue")
            ));

            long tagSize = calculateAPETagSize(testFile);

            try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
                Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                        testFile.length() - tagSize, tagSize);

                // Invalid key should be skipped
                assertNull(findField(metadata, invalidKey));
                assertFieldValue(metadata, "ValidKey", "ValidValue");
            }
        }
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Parse APE tag with maximum size values")
    void testAPEMaximumSizes() throws IOException {
        // Create large value (but within limits)
        byte[] largeValue = new byte[65536]; // 64KB
        Arrays.fill(largeValue, (byte)'A');
        String largeString = new String(largeValue, StandardCharsets.UTF_8);

        File testFile = createAPEv2File(Arrays.asList(
                new APEItem("LargeField", largeString)
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            MetadataField<?> field = findField(metadata, "LargeField");
            assertNotNull(field);
            assertEquals(largeString, field.getValue().toString());
        }
    }

    @Test
    @DisplayName("Parse APE tag with empty values")
    void testAPEEmptyValues() throws IOException {
        File testFile = createAPEv2File(Arrays.asList(
                new APEItem("EmptyField", ""),
                new APEItem("Title", "Non-empty Title"),
                new APEItem("AnotherEmpty", "")
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            // Empty fields should not be added
            assertNull(findField(metadata, "EmptyField"));
            assertNull(findField(metadata, "AnotherEmpty"));
            assertFieldValue(metadata, "Title", "Non-empty Title");
        }
    }

    @Test
    @DisplayName("Parse APE tag with padding")
    void testAPEWithPadding() throws IOException {
        File testFile = createAPEv2FileWithPadding(Arrays.asList(
                new APEItem("Title", "Test Title"),
                new APEItem("Artist", "Test Artist")
        ), 100); // 100 bytes of padding

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            // Should parse correctly despite padding
            assertFieldValue(metadata, "Title", "Test Title");
            assertFieldValue(metadata, "Artist", "Test Artist");
        }
    }

    // ==================== Flag Tests ====================

    @Test
    @DisplayName("Parse APE tag with different flag combinations")
    void testAPEFlagCombinations() throws IOException {
        // Test Header + Footer configuration
        File testFile1 = createAPEFileWithFlags(true, true, Arrays.asList(
                new APEItem("Title", "Header+Footer")
        ));

        // Test Footer only configuration
        File testFile2 = createAPEFileWithFlags(false, true, Arrays.asList(
                new APEItem("Title", "Footer Only")
        ));

        // Test both configurations
        for (File file : Arrays.asList(testFile1, testFile2)) {
            long tagSize = calculateAPETagSize(file);

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                        file.length() - tagSize, tagSize);

                assertNotNull(metadata);
                assertTrue(metadata.getFields().size() > 0);
            }
        }
    }

    @Test
    @DisplayName("Parse APE tag with read-only items")
    void testAPEReadOnlyItems() throws IOException {
        File testFile = createAPEv2FileWithFlags(Arrays.asList(
                new APEItemWithFlags("ReadOnlyField", "This is read-only", true),
                new APEItemWithFlags("NormalField", "This is normal", false)
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            // Both fields should be parsed
            assertFieldValue(metadata, "ReadOnlyField", "This is read-only");
            assertFieldValue(metadata, "NormalField", "This is normal");
        }
    }

    // ==================== External Reference Tests ====================

    @Test
    @DisplayName("Parse APE tag with external references")
    void testAPEExternalReferences() throws IOException {
        byte[] externalRef = "http://example.com/lyrics.txt".getBytes(StandardCharsets.UTF_8);

        File testFile = createAPEv2FileWithRawData(Arrays.asList(
                new APEItemRaw("Lyrics", externalRef, APEItemType.EXTERNAL),
                new APEItemRaw("Title", "Song with External Lyrics".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8)
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            MetadataField<?> lyricsField = findField(metadata, "Lyrics");
            assertNotNull(lyricsField);
            assertEquals("[EXTERNAL:http://example.com/lyrics.txt]", lyricsField.getValue().toString());
        }
    }

    // ==================== Performance Test ====================

    @Test
    @DisplayName("Performance test with many items")
    @Timeout(5) // Should complete within 5 seconds
    void testPerformanceWithManyItems() throws IOException {
        List<APEItem> items = new java.util.ArrayList<>();

        // Create 500 items
        for (int i = 0; i < 500; i++) {
            items.add(new APEItem("Field" + i, "Value " + i));
        }

        File testFile = createAPEv2File(items);
        long tagSize = calculateAPETagSize(testFile);

        long startTime = System.currentTimeMillis();

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            assertEquals(500, metadata.getFields().size());
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Parsed 500 items in " + duration + "ms");
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Test canHandle method")
    void testCanHandle() {
        assertTrue(strategy.canHandle(TagFormat.APEV1));
        assertTrue(strategy.canHandle(TagFormat.APEV2));

        assertFalse(strategy.canHandle(TagFormat.ID3V1));
        assertFalse(strategy.canHandle(TagFormat.ID3V2_3));
        assertFalse(strategy.canHandle(TagFormat.VORBIS_COMMENT));
        assertFalse(strategy.canHandle(TagFormat.MP4));
    }

    @Test
    @DisplayName("Parse real-world APE tag structure")
    void testRealWorldAPETag() throws IOException {
        // Simulate a real-world APE tag with mixed content
        File testFile = createAPEv2FileWithRawData(Arrays.asList(
                new APEItemRaw("Title", "Real World Test Song".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8),
                new APEItemRaw("Artist", createMultiValueData("Main Artist", "Featured Artist"), APEItemType.UTF8),
                new APEItemRaw("Album", "Test Album 2024".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8),
                new APEItemRaw("Year", "2024".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8),
                new APEItemRaw("Track", "3/12".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8),
                new APEItemRaw("Genre", createMultiValueData("Rock", "Alternative"), APEItemType.UTF8),
                new APEItemRaw("Comment", "Encoded with Test Encoder v1.0".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8),
                new APEItemRaw("REPLAYGAIN_TRACK_GAIN", "-6.48 dB".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8),
                new APEItemRaw("REPLAYGAIN_TRACK_PEAK", "0.977295".getBytes(StandardCharsets.UTF_8), APEItemType.UTF8),
                new APEItemRaw("Cover Art (Front)", createMockJPEGData(), APEItemType.BINARY)
        ));

        long tagSize = calculateAPETagSize(testFile);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.APEV2, raf,
                    testFile.length() - tagSize, tagSize);

            // Verify complete tag parsing
            assertEquals(10, metadata.getFields().size());
            assertFieldValue(metadata, "Title", "Real World Test Song");
            assertFieldValue(metadata, "Artist", "Main Artist; Featured Artist");
            assertFieldValue(metadata, "Genre", "Rock; Alternative");
            assertTrue(findField(metadata, "Cover Art (Front)").getValue().toString().startsWith("[IMAGE:JPEG"));
        }
    }

    // ==================== Helper Methods ====================

    private void assertFieldValue(Metadata metadata, String key, String expectedValue) {
        MetadataField<?> field = findField(metadata, key);
        assertNotNull(field, "Field " + key + " not found");
        assertEquals(expectedValue, field.getValue().toString());
    }

    private MetadataField<?> findField(Metadata metadata, String key) {
        return metadata.getFields().stream()
                .filter(f -> f.getKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    private File createAPEv2File(List<APEItem> items) throws IOException {
        return createAPEFile(2000, items);
    }

    private File createAPEFile(int version, List<APEItem> items) throws IOException {
        File file = new File(tempDir.toFile(), "test_ape.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write some dummy audio data
            raf.write(new byte[1000]);

            // Calculate tag size
            int tagSize = 32; // Footer size
            for (APEItem item : items) {
                tagSize += 8 + item.key.length() + 1 + item.value.getBytes(StandardCharsets.UTF_8).length;
            }

            // Write APE tag items
            for (APEItem item : items) {
                byte[] keyBytes = item.key.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = item.value.getBytes(StandardCharsets.UTF_8);

                // Value size (4 bytes, little-endian)
                writeInt32LE(raf, valueBytes.length);
                // Flags (4 bytes, little-endian) - UTF8 type
                writeInt32LE(raf, 0);
                // Key (null-terminated)
                raf.write(keyBytes);
                raf.write(0);
                // Value
                raf.write(valueBytes);
            }

            // Write APE footer
            raf.write("APETAGEX".getBytes());
            writeInt32LE(raf, version);
            writeInt32LE(raf, tagSize);
            writeInt32LE(raf, items.size());
            writeInt32LE(raf, 0x40000000); // Has footer, no header
            raf.write(new byte[8]); // Reserved
        }

        return file;
    }

    private File createAPEv2FileWithRawData(List<APEItemRaw> items) throws IOException {
        File file = new File(tempDir.toFile(), "test_ape_raw.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write some dummy audio data
            raf.write(new byte[1000]);

            // Calculate tag size
            int tagSize = 32; // Footer size
            for (APEItemRaw item : items) {
                tagSize += 8 + item.key.length() + 1 + item.data.length;
            }

            // Write APE tag items
            for (APEItemRaw item : items) {
                byte[] keyBytes = item.key.getBytes(StandardCharsets.UTF_8);

                // Value size (4 bytes, little-endian)
                writeInt32LE(raf, item.data.length);
                // Flags (4 bytes, little-endian)
                writeInt32LE(raf, item.type.value);
                // Key (null-terminated)
                raf.write(keyBytes);
                raf.write(0);
                // Value
                raf.write(item.data);
            }

            // Write APE footer
            raf.write("APETAGEX".getBytes());
            writeInt32LE(raf, 2000);
            writeInt32LE(raf, tagSize);
            writeInt32LE(raf, items.size());
            writeInt32LE(raf, 0x40000000); // Has footer, no header
            raf.write(new byte[8]); // Reserved
        }

        return file;
    }

    private File createAPEv2FileWithPadding(List<APEItem> items, int paddingSize) throws IOException {
        File file = new File(tempDir.toFile(), "test_ape_padding.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write some dummy audio data
            raf.write(new byte[1000]);

            // Calculate tag size including padding
            int tagSize = 32 + paddingSize; // Footer size + padding
            for (APEItem item : items) {
                tagSize += 8 + item.key.length() + 1 + item.value.getBytes(StandardCharsets.UTF_8).length;
            }

            // Write APE tag items
            for (APEItem item : items) {
                byte[] keyBytes = item.key.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = item.value.getBytes(StandardCharsets.UTF_8);

                writeInt32LE(raf, valueBytes.length);
                writeInt32LE(raf, 0);
                raf.write(keyBytes);
                raf.write(0);
                raf.write(valueBytes);
            }

            // Write padding
            raf.write(new byte[paddingSize]);

            // Write APE footer
            raf.write("APETAGEX".getBytes());
            writeInt32LE(raf, 2000);
            writeInt32LE(raf, tagSize);
            writeInt32LE(raf, items.size());
            writeInt32LE(raf, 0x40000000);
            raf.write(new byte[8]);
        }

        return file;
    }

    private File createAPEFileWithFlags(boolean hasHeader, boolean hasFooter, List<APEItem> items) throws IOException {
        File file = new File(tempDir.toFile(), "test_ape_flags.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write some dummy audio data
            raf.write(new byte[1000]);

            // Calculate tag size
            int tagSize = 0;
            if (hasHeader) tagSize += 32;
            if (hasFooter) tagSize += 32;
            for (APEItem item : items) {
                tagSize += 8 + item.key.length() + 1 + item.value.getBytes(StandardCharsets.UTF_8).length;
            }

            // Calculate flags
            int flags = 0;
            if (hasHeader) flags |= 0x80000000;
            if (hasFooter) flags |= 0x40000000;

            // Write header if present
            if (hasHeader) {
                raf.write("APETAGEX".getBytes());
                writeInt32LE(raf, 2000);
                writeInt32LE(raf, tagSize);
                writeInt32LE(raf, items.size());
                writeInt32LE(raf, flags | 0x20000000); // Is header flag
                raf.write(new byte[8]);
            }

            // Write items
            for (APEItem item : items) {
                byte[] keyBytes = item.key.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = item.value.getBytes(StandardCharsets.UTF_8);

                writeInt32LE(raf, valueBytes.length);
                writeInt32LE(raf, 0);
                raf.write(keyBytes);
                raf.write(0);
                raf.write(valueBytes);
            }

            // Write footer if present
            if (hasFooter) {
                raf.write("APETAGEX".getBytes());
                writeInt32LE(raf, 2000);
                writeInt32LE(raf, tagSize);
                writeInt32LE(raf, items.size());
                writeInt32LE(raf, flags);
                raf.write(new byte[8]);
            }
        }

        return file;
    }

    private File createAPEv2FileWithFlags(List<APEItemWithFlags> items) throws IOException {
        File file = new File(tempDir.toFile(), "test_ape_item_flags.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write some dummy audio data
            raf.write(new byte[1000]);

            // Calculate tag size
            int tagSize = 32; // Footer size
            for (APEItemWithFlags item : items) {
                tagSize += 8 + item.key.length() + 1 + item.value.getBytes(StandardCharsets.UTF_8).length;
            }

            // Write APE tag items
            for (APEItemWithFlags item : items) {
                byte[] keyBytes = item.key.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = item.value.getBytes(StandardCharsets.UTF_8);

                writeInt32LE(raf, valueBytes.length);
                // Item flags
                int itemFlags = 0;
                if (item.readOnly) itemFlags |= 0x01;
                writeInt32LE(raf, itemFlags);
                raf.write(keyBytes);
                raf.write(0);
                raf.write(valueBytes);
            }

            // Write APE footer
            raf.write("APETAGEX".getBytes());
            writeInt32LE(raf, 2000);
            writeInt32LE(raf, tagSize);
            writeInt32LE(raf, items.size());
            writeInt32LE(raf, 0x40000000);
            raf.write(new byte[8]);
        }

        return file;
    }

    private File createCorruptedAPEFile(CorruptionType type) throws IOException {
        File file = new File(tempDir.toFile(), "corrupted_ape.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.write(new byte[1000]);

            switch (type) {
                case INVALID_PREAMBLE:
                    raf.write("INVALID!".getBytes());
                    writeInt32LE(raf, 2000);
                    writeInt32LE(raf, 32);
                    writeInt32LE(raf, 0);
                    writeInt32LE(raf, 0x40000000);
                    raf.write(new byte[8]);
                    break;

                case INVALID_ITEM_COUNT:
                    raf.write("APETAGEX".getBytes());
                    writeInt32LE(raf, 2000);
                    writeInt32LE(raf, 64);
                    writeInt32LE(raf, 9999); // Way too many items
                    writeInt32LE(raf, 0x40000000);
                    raf.write(new byte[8]);
                    break;
            }
        }

        return file;
    }

    private byte[] createMultiValueData(String... values) {
        if (values.length == 0) return new byte[0];

        int totalSize = 0;
        for (String value : values) {
            totalSize += value.getBytes(StandardCharsets.UTF_8).length + 1; // +1 for null separator
        }
        totalSize--; // No null after last value

        byte[] result = new byte[totalSize];
        int pos = 0;

        for (int i = 0; i < values.length; i++) {
            byte[] valueBytes = values[i].getBytes(StandardCharsets.UTF_8);
            System.arraycopy(valueBytes, 0, result, pos, valueBytes.length);
            pos += valueBytes.length;

            if (i < values.length - 1) {
                result[pos++] = 0; // Null separator
            }
        }

        return result;
    }

    private byte[] createMockJPEGData() {
        byte[] data = new byte[1024];
        // JPEG magic number
        data[0] = (byte)0xFF;
        data[1] = (byte)0xD8;
        data[2] = (byte)0xFF;
        data[3] = (byte)0xE0;
        // Fill rest with dummy data
        for (int i = 4; i < data.length; i++) {
            data[i] = (byte)(i % 256);
        }
        return data;
    }

    private long calculateAPETagSize(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // Read footer to get tag size
            raf.seek(file.length() - 32 + 12);
            byte[] sizeBytes = new byte[4];
            raf.read(sizeBytes);
            return readInt32LE(sizeBytes, 0);
        }
    }

    private void writeInt32LE(RandomAccessFile raf, int value) throws IOException {
        raf.write(value & 0xFF);
        raf.write((value >> 8) & 0xFF);
        raf.write((value >> 16) & 0xFF);
        raf.write((value >> 24) & 0xFF);
    }

    private int readInt32LE(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    // Helper classes
    private static class APEItem {
        String key;
        String value;

        APEItem(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class APEItemRaw {
        String key;
        byte[] data;
        APEItemType type;

        APEItemRaw(String key, byte[] data, APEItemType type) {
            this.key = key;
            this.data = data;
            this.type = type;
        }
    }

    private static class APEItemWithFlags {
        String key;
        String value;
        boolean readOnly;

        APEItemWithFlags(String key, String value, boolean readOnly) {
            this.key = key;
            this.value = value;
            this.readOnly = readOnly;
        }
    }

    private enum APEItemType {
        UTF8(0x00),
        BINARY(0x02),
        EXTERNAL(0x04);

        final int value;
        APEItemType(int value) {
            this.value = value;
        }
    }

    private enum CorruptionType {
        INVALID_PREAMBLE,
        INVALID_ITEM_COUNT
    }
}