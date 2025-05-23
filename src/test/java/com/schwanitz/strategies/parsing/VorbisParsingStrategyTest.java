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

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@Tag("vorbis")
@Tag("parsing")
class VorbisParsingStrategyTest {

    private VorbisParsingStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        strategy = new VorbisParsingStrategy();
    }

    @Test
    @DisplayName("Parse valid Vorbis Comment with standard fields (Ogg)")
    void testParseStandardVorbisCommentOgg() throws IOException {
        File testFile = createVorbisCommentFile(true, // Ogg (mit Header und Framing-Bit)
                "TestVendor",
                "TITLE=Test Title",
                "ARTIST=Test Artist",
                "ALBUM=Test Album",
                "DATE=2024",
                "GENRE=Rock",
                "COMMENT=Test Comment");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertEquals("VorbisComment", metadata.getTagFormat());

            assertFieldValue(metadata, "VENDOR", "TestVendor");
            assertFieldValue(metadata, "TITLE", "Test Title");
            assertFieldValue(metadata, "ARTIST", "Test Artist");
            assertFieldValue(metadata, "ALBUM", "Test Album");
            assertFieldValue(metadata, "DATE", "2024");
            assertFieldValue(metadata, "GENRE", "Rock");
            assertFieldValue(metadata, "COMMENT", "Test Comment");
        }
    }

    @Test
    @DisplayName("Parse valid Vorbis Comment with standard fields (FLAC)")
    void testParseStandardVorbisCommentFlac() throws IOException {
        File testFile = createVorbisCommentFile(false, // FLAC (ohne Ogg-Header, kein Framing-Bit)
                "TestVendor",
                "TITLE=Test Title",
                "ARTIST=Test Artist",
                "ALBUM=Test Album",
                "TRACKNUMBER=5");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertEquals("VorbisComment", metadata.getTagFormat());

            assertFieldValue(metadata, "VENDOR", "TestVendor");
            assertFieldValue(metadata, "TITLE", "Test Title");
            assertFieldValue(metadata, "ARTIST", "Test Artist");
            assertFieldValue(metadata, "ALBUM", "Test Album");
            assertFieldValue(metadata, "TRACKNUMBER", "5");
        }
    }

    @Test
    @DisplayName("Parse Vorbis Comment with multiple values for same field")
    void testParseMultipleValues() throws IOException {
        File testFile = createVorbisCommentFile(true,
                "TestVendor",
                "ARTIST=Primary Artist",
                "ARTIST=Secondary Artist",
                "GENRE=Rock",
                "GENRE=Alternative");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertEquals("VorbisComment", metadata.getTagFormat());

            assertFieldValue(metadata, "ARTIST", "Primary Artist; Secondary Artist");
            assertFieldValue(metadata, "GENRE", "Rock; Alternative");
        }
    }

    @Test
    @DisplayName("Parse Vorbis Comment with special characters and UTF-8 encoding")
    void testParseSpecialCharacters() throws IOException {
        File testFile = createVorbisCommentFile(true,
                "TestVendor",
                "TITLE=Título con ñ",
                "ARTIST=Künstler mit ü",
                "ALBUM=Album avec é",
                "COMMENT=Комментарий");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertFieldValue(metadata, "TITLE", "Título con ñ");
            assertFieldValue(metadata, "ARTIST", "Künstler mit ü");
            assertFieldValue(metadata, "ALBUM", "Album avec é");
            assertFieldValue(metadata, "COMMENT", "Комментарий");
        }
    }

    @Test
    @DisplayName("Parse Vorbis Comment with empty vendor string")
    void testParseEmptyVendor() throws IOException {
        File testFile = createVorbisCommentFile(true,
                "",
                "TITLE=Test Title",
                "ARTIST=Test Artist");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertNull(findField(metadata, "VENDOR"), "VENDOR field should not be present");
            assertFieldValue(metadata, "TITLE", "Test Title");
            assertFieldValue(metadata, "ARTIST", "Test Artist");
        }
    }

    @Test
    @DisplayName("Parse Vorbis Comment with unknown fields")
    void testParseUnknownFields() throws IOException {
        File testFile = createVorbisCommentFile(true,
                "TestVendor",
                "CUSTOMFIELD=Custom Value",
                "TITLE=Test Title");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertFieldValue(metadata, "CUSTOMFIELD", "Custom Value");
            assertFieldValue(metadata, "TITLE", "Test Title");
        }
    }

    @Test
    @DisplayName("Handle corrupted Vorbis Comment (invalid vendor length)")
    void testCorruptedVorbisCommentInvalidVendorLength() throws IOException {
        File testFile = createCorruptedVorbisCommentFile(true, true);

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            assertThrows(IOException.class, () -> {
                strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());
            });
        }
    }

    @Test
    @DisplayName("Handle corrupted Vorbis Comment (invalid header)")
    void testCorruptedVorbisCommentInvalidHeader() throws IOException {
        File testFile = createCorruptedVorbisCommentFile(true, false);

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            assertThrows(IOException.class, () -> {
                strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());
            });
        }
    }

    @Test
    @DisplayName("Handle empty Vorbis Comment")
    void testEmptyVorbisComment() throws IOException {
        File testFile = createVorbisCommentFile(true, "TestVendor");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertFieldValue(metadata, "VENDOR", "TestVendor");
            assertEquals(1, metadata.getFields().size());
        }
    }

    @Test
    @DisplayName("Parse Vorbis Comment with large comment field")
    void testLargeCommentField() throws IOException {
        StringBuilder largeComment = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeComment.append("This is a large comment line.");
        }

        File testFile = createVorbisCommentFile(true,
                "TestVendor",
                "COMMENT=" + largeComment.toString());

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertFieldValue(metadata, "COMMENT", largeComment.toString());
        }
    }

    @Test
    @DisplayName("Parse Vorbis Comment with invalid field name")
    void testInvalidFieldName() throws IOException {
        File testFile = createVorbisCommentFile(true,
                "TestVendor",
                "INVALID=FIELD=Invalid Value",
                "TITLE=Test Title");

        TagInfo tagInfo = new TagInfo(TagFormat.VORBIS_COMMENT, 0, testFile.length());

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            Metadata metadata = strategy.parseTag(TagFormat.VORBIS_COMMENT, raf, tagInfo.getOffset(), tagInfo.getSize());

            assertNotNull(metadata);
            assertNull(findField(metadata, "INVALID=FIELD"), "Invalid field name should be ignored");
            assertFieldValue(metadata, "TITLE", "Test Title");
        }
    }

    @Test
    @DisplayName("Test canHandle method")
    void testCanHandle() {
        assertTrue(strategy.canHandle(TagFormat.VORBIS_COMMENT));
        assertFalse(strategy.canHandle(TagFormat.ID3V1));
        assertFalse(strategy.canHandle(TagFormat.MP4));
        assertFalse(strategy.canHandle(TagFormat.LYRICS3V2));
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

    private File createVorbisCommentFile(boolean isOgg, String vendor, String... comments) throws IOException {
        File file = new File(tempDir.toFile(), isOgg ? "test_vorbis_comment.ogg" : "test_vorbis_comment.flac");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            if (isOgg) {
                // Ogg Vorbis Comment Header: 0x03 + "vorbis"
                raf.writeByte(0x03);
                raf.write("vorbis".getBytes(StandardCharsets.US_ASCII));
            }

            // Vendor String
            byte[] vendorBytes = vendor.getBytes(StandardCharsets.UTF_8);
            raf.writeInt(Integer.reverseBytes(vendorBytes.length)); // Little-endian
            raf.write(vendorBytes);

            // Comment Count
            raf.writeInt(Integer.reverseBytes(comments.length)); // Little-endian

            // Write Comments
            for (String comment : comments) {
                byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_8);
                raf.writeInt(Integer.reverseBytes(commentBytes.length)); // Little-endian
                raf.write(commentBytes);
            }

            if (isOgg) {
                // Framing Bit
                raf.writeByte(1);
            }
        }

        return file;
    }

    private File createCorruptedVorbisCommentFile(boolean isOgg, boolean invalidVendorLength) throws IOException {
        File file = new File(tempDir.toFile(), "corrupted_vorbis_comment.ogg");

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            if (isOgg) {
                // Ogg Vorbis Comment Header
                if (!invalidVendorLength) {
                    // Invalid header
                    raf.writeByte(0x03);
                    raf.write("invalid".getBytes(StandardCharsets.US_ASCII)); // Wrong header
                } else {
                    raf.writeByte(0x03);
                    raf.write("vorbis".getBytes(StandardCharsets.US_ASCII));
                }
            }

            if (invalidVendorLength) {
                // Invalid vendor length
                raf.writeInt(Integer.reverseBytes(999999)); // Too large
                raf.write("TestVendor".getBytes(StandardCharsets.UTF_8));
            }
        }

        return file;
    }
}