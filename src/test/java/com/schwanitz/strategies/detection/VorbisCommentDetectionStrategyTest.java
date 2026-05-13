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

@DisplayName("Vorbis Comment Detection Strategy")
class VorbisCommentDetectionStrategyTest {

    private VorbisCommentDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new VorbisCommentDetectionStrategy();
    }

    private byte[] be24(int value) {
        return new byte[]{
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("OggS signature")
        void oggSignature() throws Exception {
            byte[] data = builder().writeString("OggS").writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("fLaC signature")
        void flacSignature() throws Exception {
            byte[] data = builder().writeString("fLaC").writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Invalid signature")
        void invalidSignature() throws Exception {
            byte[] data = builder().writeString("RIFF").writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[2];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags FLAC")
    class DetectTagsFlac {

        @Test
        @DisplayName("FLAC with Vorbis Comment block")
        void flacWithVorbisComment() throws Exception {
            byte[] streamInfoData = new byte[34];
            int vcBlockDataLen = 4 + 10 + 2;
            byte[] data = builder()
                    .writeString("fLaC")
                    .writeByte(0x00)
                    .write(be24(34))
                    .write(streamInfoData)
                    .writeByte(0x04)
                    .write(be24(vcBlockDataLen))
                    .write(le32(6))
                    .writeString("vorbis")
                    .write(le32(4))
                    .writeString("Test")
                    .write(le32(0))
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertFalse(tags.isEmpty());
            assertEquals(TagFormat.VORBIS_COMMENT, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("FLAC without Vorbis Comment block")
        void noVorbisCommentInFlac() throws Exception {
            byte[] streamInfoData = new byte[34];
            byte[] data = builder()
                    .writeString("fLaC")
                    .writeByte(0x80 | 0x00)
                    .write(be24(34))
                    .write(streamInfoData)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }
    }

    @Test
    @DisplayName("getSupportedTagFormats")
    void supportedFormats() {
        List<TagFormat> formats = strategy.getSupportedTagFormats();
        assertTrue(formats.contains(TagFormat.VORBIS_COMMENT));
    }
}