package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.APEDetectionStrategy;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.tagging.TagFormat;
import com.schwanitz.tagging.TagInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.schwanitz.strategies.detection.DetectionTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("APE Detection Strategy")
class APEDetectionStrategyTest {

    private APEDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new APEDetectionStrategy();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("APE at start of file")
        void apeAtStart() throws Exception {
            byte[] data = builder()
                    .writeString("APETAGEX")
                    .write(le32(2000))
                    .write(le32(64))
                    .write(le32(0x80000000))
                    .writeBytes(32)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("APE at end of file")
        void apeAtEnd() throws Exception {
            byte[] footer = builder()
                    .writeString("APETAGEX")
                    .write(le32(2000))
                    .write(le32(48))
                    .write(le32(0x40000000))
                    .writeBytes(12)
                    .build();
            assertEquals(32, footer.length);
            byte[] data = builder().writeBytes(200).write(footer).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

@Test
        @DisplayName("Buffer too small for 32-byte check")
        void bufferTooSmall() throws Exception {
            byte[] data = new byte[30];
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("No APE signature")
        void noSignature() throws Exception {
            byte[] data = builder().writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("APEv2 at start")
        void apev2AtStart() throws Exception {
            int tagSize = 64;
            byte[] data = builder()
                    .writeString("APETAGEX")
                    .write(le32(2000))
                    .write(le32(tagSize))
                    .write(le32(0x80000000))
                    .writeBytes(tagSize - 32)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertFalse(tags.isEmpty());
            assertEquals(TagFormat.APEV2, tags.get(0).getFormat());
            assertEquals(0, tags.get(0).getOffset());
        }

        @Test
        @DisplayName("APEv1 at end")
        void apev1AtEnd() throws Exception {
            byte[] footer = builder()
                    .writeString("APETAGEX")
                    .write(le32(1000))
                    .write(le32(48))
                    .write(le32(0))
                    .writeBytes(12)
                    .build();
            assertEquals(32, footer.length);
            byte[] data = builder().writeBytes(200).write(footer).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.stream().anyMatch(t -> t.getFormat() == TagFormat.APEV1));
        }

        @Test
        @DisplayName("Unknown version yields no tag")
        void unknownVersion() throws Exception {
            int tagSize = 64;
            byte[] data = builder()
                    .writeString("APETAGEX")
                    .write(le32(3000))
                    .write(le32(tagSize))
                    .write(le32(0x80000000))
                    .writeBytes(tagSize - 32)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            long apeTags = tags.stream().filter(t -> t.getFormat() == TagFormat.APEV1 || t.getFormat() == TagFormat.APEV2).count();
            assertEquals(0, apeTags);
        }
    }

    @Test
    @DisplayName("getSupportedTagFormats")
    void supportedFormats() {
        List<TagFormat> formats = strategy.getSupportedTagFormats();
        assertTrue(formats.contains(TagFormat.APEV1));
        assertTrue(formats.contains(TagFormat.APEV2));
    }
}