package com.schwanitz.strategies.detection;

import com.schwanitz.io.SeekableDataSource;
import com.schwanitz.io.SeekableDataSources;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class DetectionTestHelper {

    public static final int BUFFER_SIZE = 4096;

    private DetectionTestHelper() {}

    public static SeekableDataSource forBytes(byte[] data) {
        return SeekableDataSources.forBytes(data);
    }

    public static Buffers readBuffers(SeekableDataSource source) throws Exception {
        long sourceLength = source.length();
        int bufSize = (int) Math.min(BUFFER_SIZE, sourceLength);

        byte[] startBuffer = new byte[bufSize];
        int startRead = source.read(0, startBuffer, 0, bufSize);
        if (startRead < bufSize) {
            byte[] trimmed = new byte[Math.max(startRead, 0)];
            System.arraycopy(startBuffer, 0, trimmed, 0, trimmed.length);
            startBuffer = trimmed;
            return new Buffers(startBuffer, startBuffer);
        }

        byte[] endBuffer;
        if (sourceLength <= BUFFER_SIZE) {
            endBuffer = startBuffer;
        } else {
            long endPosition = sourceLength - BUFFER_SIZE;
            endBuffer = new byte[BUFFER_SIZE];
            int endRead = source.read(endPosition, endBuffer, 0, BUFFER_SIZE);
            if (endRead < BUFFER_SIZE) {
                byte[] trimmed = new byte[Math.max(endRead, 0)];
                System.arraycopy(endBuffer, 0, trimmed, 0, trimmed.length);
                endBuffer = trimmed;
            }
        }

        return new Buffers(startBuffer, endBuffer);
    }

    public record Buffers(byte[] startBuffer, byte[] endBuffer) {}

    public static byte[] le16(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }

    public static byte[] le32(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    public static byte[] le64(long value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 32) & 0xFF),
                (byte) ((value >> 40) & 0xFF),
                (byte) ((value >> 48) & 0xFF),
                (byte) ((value >> 56) & 0xFF)
        };
    }

    public static byte[] be16(int value) {
        return new byte[]{
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    public static byte[] be32(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    public static byte[] be64(long value) {
        return new byte[]{
                (byte) ((value >> 56) & 0xFF),
                (byte) ((value >> 48) & 0xFF),
                (byte) ((value >> 40) & 0xFF),
                (byte) ((value >> 32) & 0xFF),
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    public static byte[] synchsafe4(int value) {
        return new byte[]{
                (byte) ((value >> 21) & 0x7F),
                (byte) ((value >> 14) & 0x7F),
                (byte) ((value >> 7) & 0x7F),
                (byte) (value & 0x7F)
        };
    }

    public static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] pad(int count) {
        return new byte[count];
    }

    public static class Builder {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public Builder write(byte[] data) {
            baos.write(data, 0, data.length);
            return this;
        }

        public Builder writeByte(int b) {
            baos.write(b & 0xFF);
            return this;
        }

        public Builder writeLE16(int v) { return write(le16(v)); }
        public Builder writeLE32(int v) { return write(le32(v)); }
        public Builder writeLE64(long v) { return write(le64(v)); }
        public Builder writeBE16(int v) { return write(be16(v)); }
        public Builder writeBE32(int v) { return write(be32(v)); }
        public Builder writeBE64(long v) { return write(be64(v)); }
        public Builder writeSynchsafe4(int v) { return write(synchsafe4(v)); }
        public Builder writeString(String s) { return write(ascii(s)); }
        public Builder writeBytes(int count) { return write(pad(count)); }

        public byte[] build() {
            return baos.toByteArray();
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}