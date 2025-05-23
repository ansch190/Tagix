package com.schwanitz.strategies.parsing;

import com.schwanitz.interfaces.Metadata;
import com.schwanitz.others.MetadataField;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@Tag("id3")
@Tag("parsing")
class ID3ParsingStrategyTest {

    private ID3ParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new ID3ParsingStrategy();
    }

    // ==================== ID3v1 Tests ====================

    @Test
    @DisplayName("Parse valid ID3v1 tag with all fields")
    void testParseID3v1AllFields() throws IOException {
        File testFile = createID3v1File(
                "Test Title",
                "Test Artist",
                "Test Album",
                "2024",
                "Test Comment",
                (byte) 0,  // Track number position (ID3v1 - no track)
                (byte) 0,  // Track number
                (byte) 12  // Genre: Other
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V1, testFile.length() - 128, 128);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V1, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertEquals("ID3v1", metadata.getTagFormat());

            assertFieldValue(metadata, "TIT2", "Test Title");
            assertFieldValue(metadata, "TPE1", "Test Artist");
            assertFieldValue(metadata, "TALB", "Test Album");
            assertFieldValue(metadata, "TYER", "2024");
            assertFieldValue(metadata, "COMM", "Test Comment");
            assertFieldValue(metadata, "TCON", "Other");
        }
    }

    @Test
    @DisplayName("Parse ID3v1.1 tag with track number")
    void testParseID3v1_1WithTrack() throws IOException {
        File testFile = createID3v1File(
                "Track Title",
                "Track Artist",
                "Track Album",
                "2023",
                "Short Comment",  // 28 chars max for v1.1
                (byte) 0,         // Zero byte before track
                (byte) 7,         // Track number
                (byte) 17         // Genre: Rock
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V1_1, testFile.length() - 128, 128);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V1_1, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertEquals("ID3v1.1", metadata.getTagFormat());

            assertFieldValue(metadata, "TRCK", "7");
            assertFieldValue(metadata, "TCON", "Rock");
        }
    }

    @Test
    @DisplayName("Parse ID3v1 with special characters and encoding")
    void testParseID3v1SpecialCharacters() throws IOException {
        File testFile = createID3v1File(
                "Título con ñ",
                "Künstler mit ü",
                "Album avec é",
                "2022",
                "Комментарий",
                (byte) 0,
                (byte) 0,
                (byte) 88  // Eurobeat
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V1, testFile.length() - 128, 128);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V1, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            // ID3v1 uses ISO-8859-1, so some characters might not be preserved correctly
            assertTrue(metadata.getFields().size() > 0);
        }
    }

    @Test
    @DisplayName("Parse ID3v1 with extended genres (Winamp)")
    void testParseID3v1ExtendedGenres() throws IOException {
        // Test Winamp extended genres (80-147)
        File testFile = createID3v1File(
                "Extended Genre Test",
                "Test Artist",
                "Test Album",
                "2024",
                "Testing extended genres",
                (byte) 0,
                (byte) 0,
                (byte) 115  // Folklore (Winamp extension)
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V1, testFile.length() - 128, 128);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V1, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertFieldValue(metadata, "TCON", "Folklore");
        }
    }

    // ==================== ID3v2.2 Tests ====================

    @Test
    @DisplayName("Parse ID3v2.2 tag with 3-character frame IDs")
    void testParseID3v2_2Basic() throws IOException {
        File testFile = createID3v2_2File(Arrays.asList(
                new Frame22("TT2", "Title v2.2"),
                new Frame22("TP1", "Artist v2.2"),
                new Frame22("TAL", "Album v2.2"),
                new Frame22("TYE", "2022"),
                new Frame22("TCO", "(17)Rock"),
                new Frame22("TRK", "5/12")
        ));

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_2, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_2, raf, 0, tagInfo.getSize());

            assertNotNull(metadata);
            assertEquals("ID3v2.2", metadata.getTagFormat());

            assertFieldValue(metadata, "TT2", "Title v2.2");
            assertFieldValue(metadata, "TP1", "Artist v2.2");
            assertFieldValue(metadata, "TAL", "Album v2.2");
            assertFieldValue(metadata, "TYE", "2022");
            assertFieldValue(metadata, "TCO", "Rock");  // Should parse genre number
            assertFieldValue(metadata, "TRK", "5/12");
        }
    }

    // ==================== ID3v2.3 Tests ====================

    @Test
    @DisplayName("Parse ID3v2.3 with all text encodings")
    void testParseID3v2_3AllEncodings() throws IOException {
        File testFile = createID3v2_3File(Arrays.asList(
                new Frame23("TIT2", "ISO-8859-1 Title", TextEncoding.ISO_8859_1),
                new Frame23("TPE1", "UTF-16 Artist ñü", TextEncoding.UTF_16),
                new Frame23("TALB", "UTF-16BE Album", TextEncoding.UTF_16BE),
                new Frame23("TCON", "Genre", TextEncoding.ISO_8859_1)
        ));

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            assertFieldValue(metadata, "TIT2", "ISO-8859-1 Title");
            assertFieldValue(metadata, "TPE1", "UTF-16 Artist ñü");
            assertFieldValue(metadata, "TALB", "UTF-16BE Album");
        }
    }

    @Test
    @DisplayName("Parse ID3v2.3 COMM (Comment) frame")
    void testParseID3v2_3CommentFrame() throws IOException {
        File testFile = createID3v2_3FileWithComment(
                "eng",  // Language
                "Short desc",
                "This is a longer comment text that contains more information"
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            assertFieldValue(metadata, "COMM", "This is a longer comment text that contains more information");
        }
    }

    @Test
    @DisplayName("Parse ID3v2.3 TXXX (User-defined text) frame")
    void testParseID3v2_3UserDefinedText() throws IOException {
        File testFile = createID3v2_3FileWithTXXX(
                "REPLAYGAIN_TRACK_GAIN",
                "-6.5 dB"
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            assertFieldValue(metadata, "TXXX", "REPLAYGAIN_TRACK_GAIN: -6.5 dB");
        }
    }

    @Test
    @DisplayName("Parse ID3v2.3 APIC (Picture) frame")
    void testParseID3v2_3PictureFrame() throws IOException {
        byte[] imageData = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0}; // JPEG header
        File testFile = createID3v2_3FileWithAPIC(
                "image/jpeg",
                (byte) 3,  // Cover (front)
                "Cover Art",
                imageData
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            MetadataField<?> apicField = findField(metadata, "APIC");
            assertNotNull(apicField);
            String value = apicField.getValue().toString();
            assertTrue(value.contains("[PICTURE:image/jpeg,Cover (front)"));
            assertTrue(value.contains("desc:Cover Art"));
        }
    }

    // ==================== ID3v2.4 Tests ====================

    @Test
    @DisplayName("Parse ID3v2.4 with UTF-8 encoding")
    void testParseID3v2_4UTF8() throws IOException {
        File testFile = createID3v2_4File(Arrays.asList(
                new Frame24("TIT2", "UTF-8 Title: 你好世界", TextEncoding.UTF_8),
                new Frame24("TPE1", "UTF-8 Artist: Björk", TextEncoding.UTF_8),
                new Frame24("TALB", "UTF-8 Album: Ñandú", TextEncoding.UTF_8)
        ));

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_4, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_4, raf, 0, tagInfo.getSize());

            assertFieldValue(metadata, "TIT2", "UTF-8 Title: 你好世界");
            assertFieldValue(metadata, "TPE1", "UTF-8 Artist: Björk");
            assertFieldValue(metadata, "TALB", "UTF-8 Album: Ñandú");
        }
    }

    @Test
    @DisplayName("Parse ID3v2.4 with synchsafe integers")
    void testParseID3v2_4SynchsafeIntegers() throws IOException {
        // ID3v2.4 uses synchsafe integers for frame sizes
        File testFile = createID3v2_4FileWithSynchsafe();

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_4, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_4, raf, 0, tagInfo.getSize());

            assertNotNull(metadata);
            assertTrue(metadata.getFields().size() > 0);
        }
    }

    // ==================== Extended Frame Tests ====================

    @Test
    @DisplayName("Parse USLT (Unsynchronized lyrics) frame")
    void testParseUSLTFrame() throws IOException {
        String lyrics = "Verse 1:\nThis is the first verse\n\nChorus:\nThis is the chorus";
        File testFile = createID3v2_3FileWithUSLT("eng", "Lyrics", lyrics);

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            assertFieldValue(metadata, "USLT", lyrics);
        }
    }

    @Test
    @DisplayName("Parse POPM (Popularimeter) frame")
    void testParsePOPMFrame() throws IOException {
        File testFile = createID3v2_3FileWithPOPM(
                "user@example.com",
                (byte) 255,  // Maximum rating
                1000000      // Play count
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            MetadataField<?> popmField = findField(metadata, "POPM");
            assertNotNull(popmField);
            String value = popmField.getValue().toString();
            assertTrue(value.contains("user@example.com"));
            assertTrue(value.contains("Rating:255/255"));
            assertTrue(value.contains("Count:1000000"));
        }
    }

    @Test
    @DisplayName("Parse WXXX (User-defined URL) frame")
    void testParseWXXXFrame() throws IOException {
        File testFile = createID3v2_3FileWithWXXX(
                "Band Website",
                "https://example.band.com"
        );

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            assertFieldValue(metadata, "WXXX", "Band Website: https://example.band.com");
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Handle corrupted ID3v1 tag")
    void testCorruptedID3v1() throws IOException {
        File testFile = createCorruptedID3v1File();
        TagInfo tagInfo = new TagInfo(TagFormat.ID3V1, testFile.length() - 128, 128);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            assertThrows(IOException.class, () -> {
                strategy.parseTag(TagFormat.ID3V1, raf, tagInfo.getOffset(), tagInfo.getSize());
            });
        }
    }

    @Test
    @DisplayName("Handle ID3v2 with invalid frame sizes")
    void testID3v2InvalidFrameSize() throws IOException {
        File testFile = createID3v2WithInvalidFrameSize();
        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            // Should handle gracefully, not throw exception
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());
            assertNotNull(metadata);
        }
    }

    @Test
    @DisplayName("Handle empty/null frames")
    void testEmptyFrames() throws IOException {
        File testFile = createID3v2_3File(Arrays.asList(
                new Frame23("TIT2", "", TextEncoding.ISO_8859_1),
                new Frame23("TPE1", null, TextEncoding.ISO_8859_1),
                new Frame23("TALB", "Valid Album", TextEncoding.ISO_8859_1)
        ));

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            // Empty frames should not create fields
            assertNull(findField(metadata, "TIT2"));
            assertNull(findField(metadata, "TPE1"));
            assertFieldValue(metadata, "TALB", "Valid Album");
        }
    }

    // ==================== Special Cases Tests ====================

    @Test
    @DisplayName("Parse genre with numeric reference")
    void testGenreNumericReference() throws IOException {
        File testFile = createID3v2_3File(Arrays.asList(
                new Frame23("TCON", "(17)", TextEncoding.ISO_8859_1),        // Just number
                new Frame23("TPOS", "(17)Rock", TextEncoding.ISO_8859_1),    // Number + text
                new Frame23("TRCK", "Rock", TextEncoding.ISO_8859_1)         // Just text
        ));

        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            assertFieldValue(metadata, "TCON", "Rock");  // Should resolve (17) to "Rock"
        }
    }

    @Test
    @DisplayName("Parse ID3v2 with extended header")
    void testID3v2WithExtendedHeader() throws IOException {
        File testFile = createID3v2_3FileWithExtendedHeader();
        TagInfo tagInfo = new TagInfo(TagFormat.ID3V2_3, 0, calculateID3v2Size(testFile));

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.ID3V2_3, raf, 0, tagInfo.getSize());

            assertNotNull(metadata);
            // Should skip extended header correctly and parse frames
            assertTrue(metadata.getFields().size() > 0);
        }
    }

    @Test
    @DisplayName("Test canHandle method")
    void testCanHandle() {
        assertTrue(strategy.canHandle(TagFormat.ID3V1));
        assertTrue(strategy.canHandle(TagFormat.ID3V1_1));
        assertTrue(strategy.canHandle(TagFormat.ID3V2_2));
        assertTrue(strategy.canHandle(TagFormat.ID3V2_3));
        assertTrue(strategy.canHandle(TagFormat.ID3V2_4));

        assertFalse(strategy.canHandle(TagFormat.VORBIS_COMMENT));
        assertFalse(strategy.canHandle(TagFormat.APEV1));
        assertFalse(strategy.canHandle(TagFormat.MP4));
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

    private File createID3v1File(String title, String artist, String album, String year,
                                 String comment, byte zeroByte, byte track, byte genre) throws IOException {
        File file = new File(tempDir.toFile(), "test_id3v1.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write some dummy data first
            raf.write(new byte[1000]);

            // Write ID3v1 tag
            raf.write("TAG".getBytes(StandardCharsets.ISO_8859_1));
            raf.write(padString(title, 30));
            raf.write(padString(artist, 30));
            raf.write(padString(album, 30));
            raf.write(padString(year, 4));

            if (zeroByte == 0 && track > 0) {
                // ID3v1.1
                raf.write(padString(comment, 28));
                raf.write(zeroByte);
                raf.write(track);
            } else {
                // ID3v1
                raf.write(padString(comment, 30));
            }

            raf.write(genre);
        }

        return file;
    }

    private File createCorruptedID3v1File() throws IOException {
        File file = new File(tempDir.toFile(), "corrupted_id3v1.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.write(new byte[1000]);
            // Write invalid tag header
            raf.write("XXX".getBytes(StandardCharsets.ISO_8859_1));
            raf.write(new byte[125]);
        }

        return file;
    }

    private byte[] padString(String str, int length) {
        byte[] result = new byte[length];
        if (str != null) {
            byte[] strBytes = str.getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(strBytes, 0, result, 0, Math.min(strBytes.length, length));
        }
        return result;
    }

    private long calculateID3v2Size(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(6);
            byte[] sizeBytes = new byte[4];
            raf.read(sizeBytes);
            return ((sizeBytes[0] & 0x7F) << 21) | ((sizeBytes[1] & 0x7F) << 14) |
                    ((sizeBytes[2] & 0x7F) << 7) | (sizeBytes[3] & 0x7F) + 10;
        }
    }

    // Frame helper classes
    private static class Frame22 {
        String id;
        String value;
        Frame22(String id, String value) {
            this.id = id;
            this.value = value;
        }
    }

    private static class Frame23 {
        String id;
        String value;
        TextEncoding encoding;
        Frame23(String id, String value, TextEncoding encoding) {
            this.id = id;
            this.value = value;
            this.encoding = encoding;
        }
    }

    private static class Frame24 extends Frame23 {
        Frame24(String id, String value, TextEncoding encoding) {
            super(id, value, encoding);
        }
    }

    private enum TextEncoding {
        ISO_8859_1(0),
        UTF_16(1),
        UTF_16BE(2),
        UTF_8(3);

        final int code;
        TextEncoding(int code) {
            this.code = code;
        }
    }

    // Complex frame creation methods
    private File createID3v2_2File(List<Frame22> frames) throws IOException {
        File file = new File(tempDir.toFile(), "test_id3v22.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Calculate total size
            int totalSize = 0;
            for (Frame22 frame : frames) {
                totalSize += 6 + 1 + frame.value.getBytes(StandardCharsets.ISO_8859_1).length;
            }

            // Write ID3v2.2 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{2, 0}); // Version 2.0
            raf.write(0); // Flags
            writeSynchsafeInt(raf, totalSize);

            // Write frames
            for (Frame22 frame : frames) {
                byte[] valueBytes = frame.value.getBytes(StandardCharsets.ISO_8859_1);
                raf.write(frame.id.getBytes());
                // Size (3 bytes, big-endian)
                raf.write((valueBytes.length + 1) >> 16);
                raf.write((valueBytes.length + 1) >> 8);
                raf.write(valueBytes.length + 1);
                raf.write(0); // ISO-8859-1 encoding
                raf.write(valueBytes);
            }

            // Padding
            raf.write(new byte[100]);
        }

        return file;
    }

    private File createID3v2_3File(List<Frame23> frames) throws IOException {
        File file = new File(tempDir.toFile(), "test_id3v23.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Calculate total size
            int totalSize = 0;
            for (Frame23 frame : frames) {
                if (frame.value != null) {
                    totalSize += 10 + 1 + getBytesForEncoding(frame.value, frame.encoding).length;
                }
            }

            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0}); // Version 3.0
            raf.write(0); // Flags
            writeSynchsafeInt(raf, totalSize);

            // Write frames
            for (Frame23 frame : frames) {
                if (frame.value != null) {
                    byte[] valueBytes = getBytesForEncoding(frame.value, frame.encoding);
                    raf.write(frame.id.getBytes());
                    writeInt32BE(raf, valueBytes.length + 1);
                    raf.write(new byte[]{0, 0}); // Flags
                    raf.write(frame.encoding.code);
                    raf.write(valueBytes);
                }
            }

            // Padding
            raf.write(new byte[100]);
        }

        return file;
    }

    private File createID3v2_4File(List<Frame24> frames) throws IOException {
        File file = new File(tempDir.toFile(), "test_id3v24.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Calculate total size
            int totalSize = 0;
            for (Frame24 frame : frames) {
                totalSize += 10 + 1 + getBytesForEncoding(frame.value, frame.encoding).length;
            }

            // Write ID3v2.4 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{4, 0}); // Version 4.0
            raf.write(0); // Flags
            writeSynchsafeInt(raf, totalSize);

            // Write frames
            for (Frame24 frame : frames) {
                byte[] valueBytes = getBytesForEncoding(frame.value, frame.encoding);
                raf.write(frame.id.getBytes());
                writeSynchsafeInt(raf, valueBytes.length + 1); // v2.4 uses synchsafe for frame size
                raf.write(new byte[]{0, 0}); // Flags
                raf.write(frame.encoding.code);
                raf.write(valueBytes);
            }

            // Padding
            raf.write(new byte[100]);
        }

        return file;
    }

    private File createID3v2_3FileWithComment(String language, String description, String text) throws IOException {
        File file = new File(tempDir.toFile(), "test_comm.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] descBytes = description.getBytes(StandardCharsets.ISO_8859_1);
            byte[] textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
            int frameSize = 1 + 3 + descBytes.length + 1 + textBytes.length;

            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0);
            writeSynchsafeInt(raf, frameSize + 10);

            // Write COMM frame
            raf.write("COMM".getBytes());
            writeInt32BE(raf, frameSize);
            raf.write(new byte[]{0, 0}); // Flags
            raf.write(0); // ISO-8859-1
            raf.write(language.getBytes());
            raf.write(descBytes);
            raf.write(0); // Null terminator
            raf.write(textBytes);
        }

        return file;
    }

    private File createID3v2_3FileWithTXXX(String description, String value) throws IOException {
        File file = new File(tempDir.toFile(), "test_txxx.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] descBytes = description.getBytes(StandardCharsets.ISO_8859_1);
            byte[] valueBytes = value.getBytes(StandardCharsets.ISO_8859_1);
            int frameSize = 1 + descBytes.length + 1 + valueBytes.length;

            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0);
            writeSynchsafeInt(raf, frameSize + 10);

            // Write TXXX frame
            raf.write("TXXX".getBytes());
            writeInt32BE(raf, frameSize);
            raf.write(new byte[]{0, 0});
            raf.write(0); // ISO-8859-1
            raf.write(descBytes);
            raf.write(0);
            raf.write(valueBytes);
        }

        return file;
    }

    private File createID3v2_3FileWithAPIC(String mimeType, byte pictureType, String description, byte[] imageData) throws IOException {
        File file = new File(tempDir.toFile(), "test_apic.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] mimeBytes = mimeType.getBytes(StandardCharsets.ISO_8859_1);
            byte[] descBytes = description.getBytes(StandardCharsets.ISO_8859_1);
            int frameSize = 1 + mimeBytes.length + 1 + 1 + descBytes.length + 1 + imageData.length;

            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0);
            writeSynchsafeInt(raf, frameSize + 10);

            // Write APIC frame
            raf.write("APIC".getBytes());
            writeInt32BE(raf, frameSize);
            raf.write(new byte[]{0, 0});
            raf.write(0); // ISO-8859-1
            raf.write(mimeBytes);
            raf.write(0);
            raf.write(pictureType);
            raf.write(descBytes);
            raf.write(0);
            raf.write(imageData);
        }

        return file;
    }

    private File createID3v2_3FileWithUSLT(String language, String description, String lyrics) throws IOException {
        File file = new File(tempDir.toFile(), "test_uslt.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] descBytes = description.getBytes(StandardCharsets.ISO_8859_1);
            byte[] lyricsBytes = lyrics.getBytes(StandardCharsets.ISO_8859_1);
            int frameSize = 1 + 3 + descBytes.length + 1 + lyricsBytes.length;

            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0);
            writeSynchsafeInt(raf, frameSize + 10);

            // Write USLT frame
            raf.write("USLT".getBytes());
            writeInt32BE(raf, frameSize);
            raf.write(new byte[]{0, 0});
            raf.write(0); // ISO-8859-1
            raf.write(language.getBytes());
            raf.write(descBytes);
            raf.write(0);
            raf.write(lyricsBytes);
        }

        return file;
    }

    private File createID3v2_3FileWithPOPM(String email, byte rating, long playCount) throws IOException {
        File file = new File(tempDir.toFile(), "test_popm.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] emailBytes = email.getBytes(StandardCharsets.ISO_8859_1);
            int frameSize = emailBytes.length + 1 + 1 + 4; // email + null + rating + counter

            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0);
            writeSynchsafeInt(raf, frameSize + 10);

            // Write POPM frame
            raf.write("POPM".getBytes());
            writeInt32BE(raf, frameSize);
            raf.write(new byte[]{0, 0});
            raf.write(emailBytes);
            raf.write(0);
            raf.write(rating);
            writeInt32BE(raf, (int)playCount);
        }

        return file;
    }

    private File createID3v2_3FileWithWXXX(String description, String url) throws IOException {
        File file = new File(tempDir.toFile(), "test_wxxx.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] descBytes = description.getBytes(StandardCharsets.ISO_8859_1);
            byte[] urlBytes = url.getBytes(StandardCharsets.ISO_8859_1);
            int frameSize = 1 + descBytes.length + 1 + urlBytes.length;

            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0);
            writeSynchsafeInt(raf, frameSize + 10);

            // Write WXXX frame
            raf.write("WXXX".getBytes());
            writeInt32BE(raf, frameSize);
            raf.write(new byte[]{0, 0});
            raf.write(0); // ISO-8859-1
            raf.write(descBytes);
            raf.write(0);
            raf.write(urlBytes);
        }

        return file;
    }

    private File createID3v2_3FileWithExtendedHeader() throws IOException {
        File file = new File(tempDir.toFile(), "test_exthdr.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write ID3v2.3 header with extended header flag
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0x40); // Extended header flag
            writeSynchsafeInt(raf, 100); // Total size

            // Write extended header
            writeInt32BE(raf, 10); // Extended header size
            raf.write(new byte[]{0, 0}); // Flags
            writeInt32BE(raf, 0); // Padding size

            // Write a simple frame
            raf.write("TIT2".getBytes());
            writeInt32BE(raf, 11);
            raf.write(new byte[]{0, 0});
            raf.write(0);
            raf.write("Test Title".getBytes());
        }

        return file;
    }

    private File createID3v2_4FileWithSynchsafe() throws IOException {
        File file = new File(tempDir.toFile(), "test_synchsafe.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write ID3v2.4 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{4, 0});
            raf.write(0);
            writeSynchsafeInt(raf, 50);

            // Write frame with synchsafe size
            raf.write("TIT2".getBytes());
            writeSynchsafeInt(raf, 15); // Synchsafe frame size
            raf.write(new byte[]{0, 0});
            raf.write(3); // UTF-8
            raf.write("Test Title UTF8".getBytes(StandardCharsets.UTF_8));
        }

        return file;
    }

    private File createID3v2WithInvalidFrameSize() throws IOException {
        File file = new File(tempDir.toFile(), "test_invalid.mp3");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write ID3v2.3 header
            raf.write("ID3".getBytes());
            raf.write(new byte[]{3, 0});
            raf.write(0);
            writeSynchsafeInt(raf, 50);

            // Write frame with invalid size
            raf.write("TIT2".getBytes());
            writeInt32BE(raf, 999999); // Way too large
            raf.write(new byte[]{0, 0});
            raf.write(0);
            raf.write("Test".getBytes());
        }

        return file;
    }

    private byte[] getBytesForEncoding(String text, TextEncoding encoding) {
        try {
            switch (encoding) {
                case ISO_8859_1:
                    return text.getBytes(StandardCharsets.ISO_8859_1);
                case UTF_16:
                    return text.getBytes(StandardCharsets.UTF_16);
                case UTF_16BE:
                    return text.getBytes(StandardCharsets.UTF_16BE);
                case UTF_8:
                    return text.getBytes(StandardCharsets.UTF_8);
                default:
                    return text.getBytes(StandardCharsets.ISO_8859_1);
            }
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private void writeSynchsafeInt(RandomAccessFile raf, int value) throws IOException {
        raf.write((value >> 21) & 0x7F);
        raf.write((value >> 14) & 0x7F);
        raf.write((value >> 7) & 0x7F);
        raf.write(value & 0x7F);
    }

    private void writeInt32BE(RandomAccessFile raf, int value) throws IOException {
        raf.write((value >> 24) & 0xFF);
        raf.write((value >> 16) & 0xFF);
        raf.write((value >> 8) & 0xFF);
        raf.write(value & 0xFF);
    }
}