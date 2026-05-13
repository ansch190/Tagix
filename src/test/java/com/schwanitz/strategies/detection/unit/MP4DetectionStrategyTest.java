package com.schwanitz.strategies.detection.unit;

import com.schwanitz.strategies.detection.MP4DetectionStrategy;

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

@DisplayName("MP4 Detection Strategy")
class MP4DetectionStrategyTest {

    private MP4DetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MP4DetectionStrategy();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("ftyp at offset 4")
        void ftypAtOffset4() throws Exception {
            byte[] data = builder().writeBE32(20).writeString("ftyp").writeBytes(12).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("No ftyp")
        void noFtyp() throws Exception {
            byte[] data = builder().writeBE32(20).writeString("mdat").writeBytes(12).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[5];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

@Test
        @DisplayName("moov as first atom")
        void moovFirst() throws Exception {
            int ftypSize = 8 + 8;
            int moovSize = 8 + 16;
            byte[] data = builder()
                    .writeBE32(ftypSize)
                    .writeString("ftyp")
                    .writeBytes(8)
                    .writeBE32(moovSize)
                    .writeString("moov")
                    .writeBytes(16)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.MP4, tags.get(0).getFormat());
            assertEquals(ftypSize, tags.get(0).getOffset());
            assertEquals(moovSize, tags.get(0).getSize());
        }

        @Test
        @DisplayName("moov after ftyp and mdat")
        void moovAfterFtypMdat() throws Exception {
            int ftypSize = 8 + 8;
            int mdatSize = 8 + 100;
            int moovSize = 8 + 16;
            byte[] data = builder()
                    .writeBE32(ftypSize)
                    .writeString("ftyp")
                    .writeBytes(8)
                    .writeBE32(mdatSize)
                    .writeString("mdat")
                    .writeBytes(100)
                    .writeBE32(moovSize)
                    .writeString("moov")
                    .writeBytes(16)
                    .build();
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.MP4, tags.get(0).getFormat());
            assertEquals(ftypSize + mdatSize, tags.get(0).getOffset());
        }

        @Test
        @DisplayName("No moov atom")
        void noMoov() throws Exception {
            int ftypSize = 8 + 8;
            int mdatSize = 8 + 50;
            byte[] data = builder()
                    .writeBE32(ftypSize)
                    .writeString("ftyp")
                    .writeBytes(8)
                    .writeBE32(mdatSize)
                    .writeString("mdat")
                    .writeBytes(50)
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
        assertTrue(formats.contains(TagFormat.MP4));
    }
}