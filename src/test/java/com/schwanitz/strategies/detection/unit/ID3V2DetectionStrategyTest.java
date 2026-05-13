package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.ID3V2DetectionStrategy;

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

@DisplayName("ID3v2 Detection Strategy")
class ID3V2DetectionStrategyTest {

    private ID3V2DetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ID3V2DetectionStrategy();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("Valid ID3v2.3 header")
        void validHeader() throws Exception {
            byte[] start = builder().writeString("ID3").writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100).writeBytes(100).build();
            Buffers bufs = readBuffers(forBytes(start));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short (< 10 bytes)")
        void bufferTooShort() throws Exception {
            byte[] start = new byte[9];
            start[0] = 'I'; start[1] = 'D'; start[2] = '3';
            Buffers bufs = readBuffers(forBytes(start));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("No ID3 signature")
        void noId3Signature() throws Exception {
            byte[] start = builder().writeString("XXXX").writeByte(3).writeByte(0)
                    .writeByte(0).writeSynchsafe4(100).writeBytes(100).build();
            Buffers bufs = readBuffers(forBytes(start));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("ID3 at non-zero offset is not detected")
        void id3AtNonZeroOffset() throws Exception {
            byte[] start = builder().writeString("XYZ").writeString("ID3").writeByte(3).writeByte(0)
                    .writeByte(0).writeSynchsafe4(100).writeBytes(100).build();
            Buffers bufs = readBuffers(forBytes(start));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("ID3v2.3 with synchsafe size")
        void id3v23() throws Exception {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V2_3, tags.get(0).getFormat());
            assertEquals(0, tags.get(0).getOffset());
            assertEquals(110, tags.get(0).getSize());
        }

        @Test
        @DisplayName("ID3v2.2")
        void id3v22() throws Exception {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(2).writeByte(0).writeByte(0)
                    .writeSynchsafe4(50)
                    .writeBytes(60)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V2_2, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("ID3v2.4")
        void id3v24() throws Exception {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(4).writeByte(0).writeByte(0)
                    .writeSynchsafe4(200)
                    .writeBytes(210)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.ID3V2_4, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("Non-zero revision returns empty")
        void nonZeroRevision() throws Exception {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(1).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Unknown major version returns empty")
        void unknownVersion() throws Exception {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(5).writeByte(0).writeByte(0)
                    .writeSynchsafe4(100)
                    .writeBytes(110)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }

        @Test
        @DisplayName("Zero synchsafe size gives total size 10")
        void zeroSynchsafeSize() throws Exception {
            byte[] data = builder()
                    .writeString("ID3")
                    .writeByte(3).writeByte(0).writeByte(0)
                    .writeSynchsafe4(0)
                    .writeBytes(50)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(10, tags.get(0).getSize());
        }

        @Test
        @DisplayName("getSupportedTagFormats returns ID3v2 formats")
        void supportedFormats() {
            List<TagFormat> formats = strategy.getSupportedTagFormats();
            assertTrue(formats.contains(TagFormat.ID3V2_2));
            assertTrue(formats.contains(TagFormat.ID3V2_3));
            assertTrue(formats.contains(TagFormat.ID3V2_4));
        }
    }
}