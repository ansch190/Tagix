package com.schwanitz.strategies.detection;

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

@DisplayName("TTA Detection Strategy")
class TTADetectionStrategyTest {

    private TTADetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TTADetectionStrategy();
    }

    private byte[] ttaHeader(String version, int channels) {
        return builder()
                .writeString(version)
                .write(le16(1))
                .write(le16(channels))
                .write(le16(16))
                .write(le32(44100))
                .write(le32(0))
                .write(le32(0))
                .build();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("TTA1 signature")
        void tta1Signature() throws Exception {
            byte[] data = ttaHeader("TTA1", 2);
            byte[] padded = builder().write(data).writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(padded));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("TTA2 signature")
        void tta2Signature() throws Exception {
            byte[] data = ttaHeader("TTA2", 2);
            byte[] padded = builder().write(data).writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(padded));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[2];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("No TTA signature")
        void noSignature() throws Exception {
            byte[] data = builder().writeString("RIFF").writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("TTA signature at non-zero offset")
        void signatureAtNonZeroOffset() throws Exception {
            byte[] data = builder()
                    .writeBytes(100)
                    .write(ttaHeader("TTA1", 2))
                    .writeBytes(200)
                    .build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("Valid TTA1 header returns empty list (format recognizer)")
        void validTTA1Header() throws Exception {
            byte[] data = builder().write(ttaHeader("TTA1", 2)).writeBytes(1000).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Valid TTA2 header returns empty list")
        void validTTA2Header() throws Exception {
            byte[] data = builder().write(ttaHeader("TTA2", 6)).writeBytes(1000).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Invalid channel count 0")
        void invalidChannelCount0() throws Exception {
            byte[] data = builder().write(ttaHeader("TTA1", 0)).writeBytes(1000).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Invalid channel count > 32")
        void invalidChannelCountTooHigh() throws Exception {
            byte[] data = builder().write(ttaHeader("TTA1", 33)).writeBytes(1000).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("TTA signature found at non-zero offset via bulk search")
        void signatureAtNonZeroOffset() throws Exception {
            byte[] data = builder()
                    .writeBytes(50)
                    .write(ttaHeader("TTA1", 2))
                    .writeBytes(1000)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("No TTA signature in file")
        void noTTASignature() throws Exception {
            byte[] data = builder().writeBytes(1000).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Test
    @DisplayName("getSupportedTagFormats")
    void supportedFormats() {
        List<TagFormat> formats = strategy.getSupportedTagFormats();
        assertTrue(formats.contains(TagFormat.TTA_METADATA));
    }
}