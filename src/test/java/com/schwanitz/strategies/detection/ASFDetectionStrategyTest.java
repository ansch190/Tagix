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

@DisplayName("ASF Detection Strategy")
class ASFDetectionStrategyTest {

    private ASFDetectionStrategy strategy;

    private static final byte[] ASF_HEADER_GUID = {
            0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11,
            (byte) 0xA6, (byte) 0xD9, 0x00, (byte) 0xAA, 0x00, 0x62, (byte) 0xCE, 0x6C
    };

    private static final byte[] CONTENT_DESC_GUID = {
            0x33, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11,
            (byte) 0xA6, (byte) 0xD9, 0x00, (byte) 0xAA, 0x00, 0x62, (byte) 0xCE, 0x6C
    };

    private static final byte[] EXT_CONTENT_DESC_GUID = {
            0x40, (byte) 0xA4, (byte) 0xD0, (byte) 0xD2, 0x07, (byte) 0xE3, (byte) 0xD2, 0x11,
            (byte) 0x97, (byte) 0xF0, 0x00, (byte) 0xA0, (byte) 0xC9, 0x5E, (byte) 0xA8, 0x50
    };

    @BeforeEach
    void setUp() {
        strategy = new ASFDetectionStrategy();
    }

    private byte[] asfFile(byte[]... objects) {
        Builder b = builder();
        int headerSize = 30;
        for (byte[] obj : objects) {
            headerSize += obj.length;
        }
        b.write(ASF_HEADER_GUID);
        b.write(le64(headerSize));
        b.write(le32(objects.length));
        b.write(le16(0x0102));
        for (byte[] obj : objects) {
            b.write(obj);
        }
        return b.build();
    }

    private byte[] asfObject(byte[] guid, long size) {
        Builder b = builder();
        b.write(guid);
        b.write(le64(size));
        b.writeBytes((int) size - 24);
        return b.build();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("ASF header GUID")
        void asfHeaderGuid() throws Exception {
            byte[] data = asfFile(asfObject(CONTENT_DESC_GUID, 24 + 50));
            Buffers bufs = readBuffers(forBytes(data));
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Invalid GUID")
        void invalidGuid() throws Exception {
            byte[] data = builder().writeBytes(200).build();
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }

        @Test
        @DisplayName("Buffer too short")
        void bufferTooShort() throws Exception {
            byte[] data = new byte[10];
            Buffers bufs = readBuffers(forBytes(data));
            assertFalse(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
        }
    }

    @Nested
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("ASF with Content Description")
        void contentDescription() throws Exception {
            byte[] contentObj = asfObject(CONTENT_DESC_GUID, 24 + 50);
            byte[] data = asfFile(contentObj);
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.ASF_CONTENT_DESC, tags.get(0).getFormat());
            assertEquals(30, tags.get(0).getOffset());
        }

        @Test
        @DisplayName("ASF with Extended Content Description")
        void extContentDescription() throws Exception {
            byte[] extObj = asfObject(EXT_CONTENT_DESC_GUID, 24 + 50);
            byte[] data = asfFile(extObj);
            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertEquals(1, tags.size());
            assertEquals(TagFormat.ASF_EXT_CONTENT_DESC, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("Invalid header size returns empty")
        void invalidHeaderSize() throws Exception {
            byte[] data = builder()
                    .write(ASF_HEADER_GUID)
                    .write(le64(20))
                    .write(le32(1))
                    .write(le16(0x0102))
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
        assertTrue(formats.contains(TagFormat.ASF_CONTENT_DESC));
        assertTrue(formats.contains(TagFormat.ASF_EXT_CONTENT_DESC));
    }
}