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

@DisplayName("ID3v1 Detection Strategy")
class ID3V1DetectionStrategyTest {

    private ID3V1DetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ID3V1DetectionStrategy();
    }

    private byte[] withID3v1(boolean withTrack) {
        Builder b = builder();
        b.writeBytes(500);
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

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("TAG at end of file")
        void tagAtEnd() throws Exception {
            byte[] data = withID3v1(false);
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[50];
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("No TAG marker")
        void noTagMarker() throws Exception {
            byte[] data = builder().writeBytes(500).build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("ID3v1.1 with track number")
        void id3v11() throws Exception {
            byte[] data = withID3v1(true);
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V1_1, tags.get(0).getFormat());
            assertEquals(128, tags.get(0).getSize());
            assertEquals(data.length - 128, tags.get(0).getOffset());
        }

        @Test
        @DisplayName("ID3v1 without track number")
        void id3v1() throws Exception {
            byte[] data = withID3v1(false);
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V1, tags.get(0).getFormat());
            assertEquals(128, tags.get(0).getSize());
        }

        @Test
        @DisplayName("getSupportedTagFormats")
        void supportedFormats() {
            List<TagFormat> formats = strategy.getSupportedTagFormats();
            assertTrue(formats.contains(TagFormat.ID3V1));
            assertTrue(formats.contains(TagFormat.ID3V1_1));
        }
    }
}