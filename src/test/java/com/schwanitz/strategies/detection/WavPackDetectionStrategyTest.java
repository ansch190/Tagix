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

@DisplayName("WavPack Detection Strategy")
class WavPackDetectionStrategyTest {

    private WavPackDetectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new WavPackDetectionStrategy();
    }

    private byte[] wvBlock(int flags, byte[]... subBlocks) {
        int totalSubData = 0;
        for (byte[] sb : subBlocks) {
            totalSubData += sb.length;
        }
        int blockSize = 32 + totalSubData;

        Builder b = builder();
        b.writeString("wvpk");          // 0-3: signature
        b.write(le32(blockSize));       // 4-7: block size
        b.writeLE16(0x0410);           // 8-9: version
        b.writeLE16(0);               // 10-11: track_no
        b.writeLE32(0);               // 12-15: total_samples
        b.writeLE32(44100);           // 16-19: sample_rate
        b.writeLE32(0);               // 20-23: block_samples
        b.writeLE32(flags);            // 24-27: flags
        b.writeLE32(0);               // 28-31: crc
        for (byte[] sb : subBlocks) {
            b.write(sb);
        }
        return b.build();
    }

    private byte[] smallSubBlock(int id, int dataSize) {
        int sizeField = dataSize / 2;
        return builder()
                .writeByte(id)
                .writeByte(sizeField)
                .writeBytes(dataSize)
                .build();
    }

    @Nested
    @DisplayName("canDetect")
    class CanDetect {

        @Test
        @DisplayName("wvpk signature")
        void wvpkSignature() throws Exception {
            byte[] data = builder().writeString("wvpk").writeBytes(200).build();
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
    @DisplayName("detectTags")
    class DetectTags {

        @Test
        @DisplayName("Block with RIFF Header sub-block (0x21)")
        void riffHeaderSubBlock() throws Exception {
            byte[] data = wvBlock(0x02, smallSubBlock(0x21, 10));

            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertFalse(tags.isEmpty());
            assertEquals(TagFormat.WAVPACK_NATIVE, tags.get(0).getFormat());
        }

        @Test
        @DisplayName("Block without INITIAL_BLOCK flag is skipped")
        void noInitialBlock() throws Exception {
            byte[] data = wvBlock(0x00, smallSubBlock(0x21, 10));

            SeekableDataSource src = forBytes(data);
            Buffers bufs = readBuffers(src);
            assertTrue(strategy.canDetect(bufs.startBuffer(), bufs.endBuffer()));
            List<TagInfo> tags = strategy.detectTags(src, bufs.startBuffer(), bufs.endBuffer());
            assertTrue(tags.isEmpty());
        }
    }

    @Test
    @DisplayName("getSupportedTagFormats")
    void supportedFormats() {
        List<TagFormat> formats = strategy.getSupportedTagFormats();
        assertTrue(formats.contains(TagFormat.WAVPACK_NATIVE));
    }
}