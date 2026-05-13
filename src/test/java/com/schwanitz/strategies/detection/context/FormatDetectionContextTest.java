package com.schwanitz.strategies.detection.context;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.strategies.detection.DetectionTestHelper;
import com.schwanitz.tagging.FormatPriorityManager;
import com.schwanitz.tagging.ScanConfiguration;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.schwanitz.strategies.detection.DetectionTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FormatDetectionContext Integration Tests")
class FormatDetectionContextTest {

    private FormatDetectionContext context;

    @BeforeEach
    void setUp() {
        context = new FormatDetectionContext();
    }

    @Nested
    @DisplayName("Full Scan")
    class FullScan {

        @Test
        @DisplayName("Detects single ID3v2.3 tag")
        void singleID3v2Tag() throws IOException {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V2_3, tags.get(0).getFormat());
            assertEquals(0, tags.get(0).getOffset());
            assertEquals(110, tags.get(0).getSize());
        }

        @Test
        @DisplayName("File with no recognizable tags returns empty list")
        void noTagsReturnsEmpty() throws IOException {
            byte[] data = builder().writeBytes(500).build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Empty source returns empty list")
        void emptySource() throws IOException {
            byte[] data = new byte[0];
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Small file (< 4KB) - startBuffer equals endBuffer")
        void smallFileStartEqualsEnd() throws IOException {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(10)
                    .writeBytes(20)
                    .build();
            assertTrue(data.length < BUFFER_SIZE, "Test data should be smaller than buffer size");
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            assertFalse(tags.isEmpty());
            assertEquals(TagFormat.ID3V2_3, tags.get(0).getFormat());
        }
    }

    @Nested
    @DisplayName("Comfort Scan")
    class ComfortScan {

        @Test
        @DisplayName("Known extension filters to relevant formats")
        void knownExtensionFiltersFormats() throws IOException {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = withName(forBytes(data), "test.mp3");

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.comfortScan());

            Set<TagFormat> comfortFormats = Set.copyOf(
                    FormatPriorityManager.getComfortScanPriority("mp3"));
            for (TagInfo tag : tags) {
                assertTrue(comfortFormats.contains(tag.getFormat()),
                        "Found " + tag.getFormat() + " which is not in comfort scan for .mp3");
            }
        }

        @Test
        @DisplayName("Unknown extension falls back to full scan")
        void unknownExtensionFallsBackToFullScan() throws IOException {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = withName(forBytes(data), "test.xyz");

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.comfortScan());

            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V2_3, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("MP3 extension detects ID3v2 and ID3v1 together")
        void mp3ExtensionDetectsMultipleTags() throws IOException {
            byte[] id3v1Tag = buildID3v1Tag(true);
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = withName(forBytes(data), "test.mp3");

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.comfortScan());

            assertTrue(tags.size() >= 2, "Should find at least ID3v2.3 and ID3v1.1");
            Set<TagFormat> foundFormats = tags.stream()
                    .map(TagInfo::getFormat)
                    .collect(Collectors.toSet());
            assertTrue(foundFormats.contains(TagFormat.ID3V2_3), "Should find ID3v2.3");
            assertTrue(foundFormats.contains(TagFormat.ID3V1_1), "Should find ID3v1.1");
        }
    }

    @Nested
    @DisplayName("Custom Scan")
    class CustomScan {

        @Test
        @DisplayName("Filters to only requested format")
        void filtersToRequestedFormat() throws IOException {
            byte[] id3v1Tag = buildID3v1Tag(true);
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src,
                    ScanConfiguration.customScan(TagFormat.ID3V2_3));

            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V2_3, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("Multiple requested formats")
        void multipleRequestedFormats() throws IOException {
            byte[] id3v1Tag = buildID3v1Tag(true);
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src,
                    ScanConfiguration.customScan(TagFormat.ID3V2_3, TagFormat.ID3V1_1));

            assertEquals(2, tags.size());
            Set<TagFormat> foundFormats = tags.stream()
                    .map(TagInfo::getFormat)
                    .collect(Collectors.toSet());
            assertTrue(foundFormats.contains(TagFormat.ID3V2_3));
            assertTrue(foundFormats.contains(TagFormat.ID3V1_1));
        }

        @Test
        @DisplayName("Requested format not present returns empty list")
        void formatNotPresentReturnsEmpty() throws IOException {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src,
                    ScanConfiguration.customScan(TagFormat.MATROSKA_TAGS));

            assertTrue(tags.isEmpty());
        }
    }

    @Nested
    @DisplayName("Multi-Tag Detection")
    class MultiTagDetection {

        @Test
        @DisplayName("MP3 with ID3v2.3 + ID3v1.1")
        void mp3WithID3v2AndID3v1() throws IOException {
            byte[] id3v1Tag = buildID3v1Tag(true);
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            assertEquals(2, tags.size());
            TagInfo id3v2 = tags.stream()
                    .filter(t -> t.getFormat() == TagFormat.ID3V2_3)
                    .findFirst().orElseThrow();
            TagInfo id3v1 = tags.stream()
                    .filter(t -> t.getFormat() == TagFormat.ID3V1_1)
                    .findFirst().orElseThrow();

            assertEquals(0, id3v2.getOffset());
            assertEquals(data.length - 128, id3v1.getOffset());
            assertEquals(128, id3v1.getSize());
        }

        @Test
        @DisplayName("MP3 with ID3v2.3 + ID3v1.1 + Lyrics3v2")
        void mp3WithID3v2AndID3v1AndLyrics3() throws IOException {
            String lyricsContent = "Some lyrics content here";
            int contentLen = lyricsContent.length();
            int tagBodySize = 11 + contentLen;
            String sizeStr = String.format("%06d", tagBodySize);

            byte[] id3v1Tag = buildID3v1Tag(true);
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .writeString("LYRICSBEGIN")
                    .writeString(lyricsContent)
                    .writeString(sizeStr)
                    .writeString("LYRICS200")
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            Set<TagFormat> foundFormats = tags.stream()
                    .map(TagInfo::getFormat)
                    .collect(Collectors.toSet());

            assertTrue(foundFormats.contains(TagFormat.ID3V2_3), "Should find ID3v2.3");
            assertTrue(foundFormats.contains(TagFormat.ID3V1_1), "Should find ID3v1.1");
            assertTrue(foundFormats.contains(TagFormat.LYRICS3V2), "Should find Lyrics3v2");
            assertEquals(3, tags.size(), "Should find exactly 3 tags");
        }

        @Test
        @DisplayName("MP3 with ID3v2.3 + ID3v1 + Lyrics3v1")
        void mp3WithID3v2AndID3v1AndLyrics3v1() throws IOException {
            byte[] id3v1Tag = buildID3v1Tag(false);
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .writeString("LYRICSBEGIN")
                    .writeBytes(50)
                    .writeString("LYRICSEND")
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            Set<TagFormat> foundFormats = tags.stream()
                    .map(TagInfo::getFormat)
                    .collect(Collectors.toSet());

            assertTrue(foundFormats.contains(TagFormat.ID3V2_3), "Should find ID3v2.3");
            assertTrue(foundFormats.contains(TagFormat.ID3V1), "Should find ID3v1");
            assertTrue(foundFormats.contains(TagFormat.LYRICS3V1), "Should find Lyrics3v1");
        }

        @Test
        @DisplayName("APEv2 at start + ID3v1 at end")
        void apeAtStartAndID3v1AtEnd() throws IOException {
            int apeTagSize = 64;
            byte[] id3v1Tag = buildID3v1Tag(false);
            byte[] data = builder()
                    .writeString("APETAGEX")
                    .write(le32(2000))
                    .write(le32(apeTagSize))
                    .write(le32(0x80000000))
                    .writeBytes(apeTagSize - 32)
                    .writeBytes(200)
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            Set<TagFormat> foundFormats = tags.stream()
                    .map(TagInfo::getFormat)
                    .collect(Collectors.toSet());
            assertTrue(foundFormats.contains(TagFormat.APEV2), "Should find APEv2");
            assertTrue(foundFormats.contains(TagFormat.ID3V1), "Should find ID3v1");
        }

        @Test
        @DisplayName("Custom scan filters multi-tag result to single format")
        void customScanFiltersMultiTagResult() throws IOException {
            byte[] id3v1Tag = buildID3v1Tag(true);
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .write(id3v1Tag)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src,
                    ScanConfiguration.customScan(TagFormat.ID3V1_1));

            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V1_1, tags.get(0).getFormat());
            assertEquals(data.length - 128, tags.get(0).getOffset());
            assertEquals(128, tags.get(0).getSize());
        }

        @Test
        @DisplayName("FLAC with Vorbis Comment block")
        void flacWithVorbisComment() throws IOException {
            String vendorString = "libFLAC";
            byte[] vendorBytes = vendorString.getBytes(StandardCharsets.UTF_8);
            int vcBlockSize = 4 + vendorBytes.length + 4;

            byte[] data = builder()
                    .writeString("fLaC")
                    .writeByte(0x04)
                    .writeBE24(vcBlockSize)
                    .write(le32(vendorBytes.length))
                    .write(vendorBytes)
                    .write(le32(0))
                    .writeByte(0x80 | 0x00)
                    .writeBE24(0)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            Set<TagFormat> foundFormats = tags.stream()
                    .map(TagInfo::getFormat)
                    .collect(Collectors.toSet());
            assertTrue(foundFormats.contains(TagFormat.VORBIS_COMMENT), "Should find VorbisComment");
        }

        @Test
        @DisplayName("FLAC with ID3v2 header finds ID3v2 but not VorbisComment")
        void flacWithID3v2Preamble() throws IOException {
            String vendorString = "libFLAC";
            byte[] vendorBytes = vendorString.getBytes(StandardCharsets.UTF_8);
            int vcBlockSize = 4 + vendorBytes.length + 4;

            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(4).writeByte(0).writeByte(0)
                    .writeSynchsafe4(10)
                    .writeBytes(10)
                    .writeString("fLaC")
                    .writeByte(0x04)
                    .writeBE24(vcBlockSize)
                    .write(le32(vendorBytes.length))
                    .write(vendorBytes)
                    .write(le32(0))
                    .writeByte(0x80 | 0x00)
                    .writeBE24(0)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> tags = context.detectTags(src, ScanConfiguration.fullScan());

            Set<TagFormat> foundFormats = tags.stream()
                    .map(TagInfo::getFormat)
                    .collect(Collectors.toSet());
            assertTrue(foundFormats.contains(TagFormat.ID3V2_4), "Should find ID3v2.4");
            assertFalse(foundFormats.contains(TagFormat.VORBIS_COMMENT),
                    "VorbisComment not detected when ID3v2 precedes fLaC (canDetect checks startBuffer)");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Source name with no extension treated as unknown in comfort scan")
        void noExtensionComfortScan() throws IOException {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = forBytes(data);

            List<TagInfo> comfortTags = context.detectTags(src, ScanConfiguration.comfortScan());

            List<TagInfo> fullTags = context.detectTags(src, ScanConfiguration.fullScan());

            assertEquals(fullTags.size(), comfortTags.size(),
                    "No extension should fall back to full scan in comfort mode");
        }

        @Test
        @DisplayName("Null source throws NullPointerException")
        void nullSourceThrowsNPE() {
            assertThrows(NullPointerException.class, () ->
                    context.detectTags(null, ScanConfiguration.fullScan()));
        }

        @Test
        @DisplayName("Null config throws NullPointerException")
        void nullConfigThrowsNPE() {
            SeekableDataSource src = forBytes(new byte[]{1, 2, 3});
            assertThrows(NullPointerException.class, () ->
                    context.detectTags(src, null));
        }
    }

    private byte[] buildID3v1Tag(boolean withTrack) {
        Builder b = builder();
        b.writeString("TAG");
        b.writeBytes(30);
        b.writeBytes(30);
        b.writeBytes(30);
        b.writeBytes(4);
        if (withTrack) {
            b.writeBytes(28);
            b.writeByte(0);
            b.writeByte(7);
        } else {
            b.writeBytes(30);
        }
        b.writeByte(17);
        return b.build();
    }
}