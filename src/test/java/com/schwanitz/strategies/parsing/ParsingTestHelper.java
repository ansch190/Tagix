package com.schwanitz.strategies.parsing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ParsingTestHelper {

    private ParsingTestHelper() {}

    private static void writeBytes(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }

    // === Byte-Order Encoding ===

    public static byte[] le16(int value) {
        return new byte[]{(byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF)};
    }

    public static byte[] le32(int value) {
        return new byte[]{
                (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF), (byte) ((value >> 24) & 0xFF)
        };
    }

    public static byte[] le64(long value) {
        return new byte[]{
                (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF), (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 32) & 0xFF), (byte) ((value >> 40) & 0xFF),
                (byte) ((value >> 48) & 0xFF), (byte) ((value >> 56) & 0xFF)
        };
    }

    public static byte[] be16(int value) {
        return new byte[]{(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    public static byte[] be24(int value) {
        return new byte[]{(byte) ((value >> 16) & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    public static byte[] be32(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF), (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)
        };
    }

    public static byte[] be64(long value) {
        return new byte[]{
                (byte) ((value >> 56) & 0xFF), (byte) ((value >> 48) & 0xFF),
                (byte) ((value >> 40) & 0xFF), (byte) ((value >> 32) & 0xFF),
                (byte) ((value >> 24) & 0xFF), (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)
        };
    }

    public static byte[] synchsafe4(int value) {
        return new byte[]{
                (byte) ((value >> 21) & 0x7F), (byte) ((value >> 14) & 0x7F),
                (byte) ((value >> 7) & 0x7F), (byte) (value & 0x7F)
        };
    }

    public static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] utf16le(String s) {
        return s.getBytes(StandardCharsets.UTF_16LE);
    }

    public static byte[] utf16be(String s) {
        return s.getBytes(StandardCharsets.UTF_16BE);
    }

    public static byte[] nullTerm(String s, Charset cs) {
        byte[] textBytes = s.getBytes(cs);
        byte[] nullTerm = new byte[textBytes.length + (cs.equals(StandardCharsets.UTF_16) || cs.equals(StandardCharsets.UTF_16BE) ? 2 : 1)];
        System.arraycopy(textBytes, 0, nullTerm, 0, textBytes.length);
        return nullTerm;
    }

    public static byte[] pad(int count) {
        return new byte[count];
    }

    public static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (byte[] arr : arrays) {
            baos.write(arr, 0, arr.length);
        }
        return baos.toByteArray();
    }

    // === Temp File Helper ===

    public static File writeTempFile(File dir, String name, byte[] data) throws IOException {
        File file = new File(dir, name);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.write(data);
        }
        return file;
    }

    public static File writeTempFile(File dir, String name, byte[] prefix, byte[] data, byte[] suffix) throws IOException {
        File file = new File(dir, name);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            if (prefix != null) raf.write(prefix);
            raf.write(data);
            if (suffix != null) raf.write(suffix);
        }
        return file;
    }

    // === Assertion Helpers ===

    public static String findFieldValue(com.schwanitz.interfaces.Metadata metadata, String key) {
        return metadata.getFields().stream()
                .filter(f -> f.getKey().equals(key))
                .map(f -> f.getValue().toString())
                .findFirst()
                .orElse(null);
    }

    public static boolean hasField(com.schwanitz.interfaces.Metadata metadata, String key) {
        return metadata.getFields().stream().anyMatch(f -> f.getKey().equals(key));
    }

    // === ID3v1 Builder ===

    public static byte[] buildID3v1Tag(String title, String artist, String album, String year,
                                        String comment, byte track, byte genre) {
        byte[] tag = new byte[128];
        System.arraycopy(ascii("TAG"), 0, tag, 0, 3);
        writeFixedField(tag, 3, 30, title != null ? title : "");
        writeFixedField(tag, 33, 30, artist != null ? artist : "");
        writeFixedField(tag, 63, 30, album != null ? album : "");
        writeFixedField(tag, 93, 4, year != null ? year : "");
        if (track > 0) {
            writeFixedField(tag, 97, 28, comment != null ? comment : "");
            tag[125] = 0;
            tag[126] = track;
        } else {
            writeFixedField(tag, 97, 30, comment != null ? comment : "");
        }
        tag[127] = genre;
        return tag;
    }

    private static void writeFixedField(byte[] dest, int offset, int len, String value) {
        byte[] src = value.getBytes(StandardCharsets.ISO_8859_1);
        int copyLen = Math.min(src.length, len);
        System.arraycopy(src, 0, dest, offset, copyLen);
    }

    // === ID3v2 Builder ===

    public static byte[] buildID3v2Header(int version, int flags, int tagSize) {
        byte[] header = new byte[10];
        System.arraycopy(ascii("ID3"), 0, header, 0, 3);
        header[3] = (byte) version;
        header[4] = (byte) 0;
        header[5] = (byte) flags;
        if (version == 4) {
            System.arraycopy(synchsafe4(tagSize), 0, header, 6, 4);
        } else {
            System.arraycopy(be32(tagSize), 0, header, 6, 4);
            header[6] = (byte) ((tagSize >> 24) & 0xFF);
            header[7] = (byte) ((tagSize >> 16) & 0xFF);
            header[8] = (byte) ((tagSize >> 8) & 0xFF);
            header[9] = (byte) (tagSize & 0xFF);
        }
        return header;
    }

    public static byte[] buildID3v2Frame(int version, String frameId, byte[] frameData) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] idBytes = frameId.getBytes(StandardCharsets.US_ASCII);
        int idLen = (version == 2) ? 3 : 4;
        baos.write(idBytes, 0, idLen);
        if (version == 2) {
            baos.write(be24(frameData.length), 0, 3);
        } else if (version == 4) {
            baos.write(synchsafe4(frameData.length), 0, 4);
        } else {
            baos.write(be32(frameData.length), 0, 4);
        }
        if (version >= 3) {
            baos.write(0);
            baos.write(0);
        }
        baos.write(frameData, 0, frameData.length);
        return baos.toByteArray();
    }

    public static byte[] buildID3v2TextFrame(int version, String frameId, int encoding, String text) {
        byte[] textBytes;
        Charset cs = switch (encoding) {
            case 0 -> StandardCharsets.ISO_8859_1;
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        textBytes = text.getBytes(cs);
        byte[] frameData = new byte[1 + textBytes.length + (cs.equals(StandardCharsets.UTF_16) || cs.equals(StandardCharsets.UTF_16BE) ? 2 : (encoding == 0 || encoding == 3 ? 1 : 2))];
        frameData[0] = (byte) encoding;
        System.arraycopy(textBytes, 0, frameData, 1, textBytes.length);
        int nullSize = (encoding == 1 || encoding == 2) ? 2 : 1;
        byte[] trimmed = new byte[1 + textBytes.length];
        trimmed[0] = (byte) encoding;
        System.arraycopy(textBytes, 0, trimmed, 1, textBytes.length);
        return buildID3v2Frame(version, frameId, trimmed);
    }

    // === APE Builder ===

    public static byte[] buildAPETag(int version, boolean hasHeader, APEItem... items) {
        ByteArrayOutputStream itemsData = new ByteArrayOutputStream();
        for (APEItem item : items) {
            writeBytes(itemsData, buildAPEItem(item));
        }
        byte[] itemsBytes = itemsData.toByteArray();
        int tagSize = itemsBytes.length + 32 + (hasHeader ? 32 : 0);
        int flags = (hasHeader ? 0x80000000 : 0) | 0x40000000;

        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        if (hasHeader) {
            writeBytes(tag, ascii("APETAGEX"));
            writeBytes(tag, le32(version));
            writeBytes(tag, le32(tagSize));
            writeBytes(tag, le32(items.length));
            writeBytes(tag, le32(flags | 0x20000000));
            writeBytes(tag, pad(8));
        }

        writeBytes(tag, itemsBytes);

        writeBytes(tag, ascii("APETAGEX"));
        writeBytes(tag, le32(version));
        writeBytes(tag, le32(tagSize));
        writeBytes(tag, le32(items.length));
        writeBytes(tag, le32(flags));
        writeBytes(tag, pad(8));

        return tag.toByteArray();
    }

    public static byte[] buildAPEItem(APEItem item) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] keyBytes = item.key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = item.value.getBytes(StandardCharsets.UTF_8);
        int itemFlags = item.itemType & 0x06;
        if (item.readOnly) itemFlags |= 0x01;

        baos.write(le32(valueBytes.length), 0, 4);
        baos.write(le32(itemFlags), 0, 4);
        baos.write(keyBytes, 0, keyBytes.length);
        baos.write(0);
        baos.write(valueBytes, 0, valueBytes.length);
        return baos.toByteArray();
    }

    public record APEItem(String key, String value, int itemType, boolean readOnly) {
        public APEItem(String key, String value) {
            this(key, value, 0, false);
        }

        public APEItem(String key, String value, int itemType) {
            this(key, value, itemType, false);
        }
    }

    // === Vorbis Comment Builder ===

    public static byte[] buildVorbisComment(boolean isOgg, String vendor, String... comments) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (isOgg) {
            baos.write(0x03);
            writeBytes(baos, ascii("vorbis"));
        }

        byte[] vendorBytes = vendor.getBytes(StandardCharsets.UTF_8);
        writeBytes(baos, le32(vendorBytes.length));
        writeBytes(baos, vendorBytes);

        int validComments = 0;
        ByteArrayOutputStream commentData = new ByteArrayOutputStream();
        for (String comment : comments) {
            if (comment != null) {
                byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_8);
                writeBytes(commentData, le32(commentBytes.length));
                writeBytes(commentData, commentBytes);
                validComments++;
            }
        }
        writeBytes(baos, le32(validComments));
        writeBytes(baos, commentData.toByteArray());

        if (isOgg) {
            baos.write(1);
        }

        return baos.toByteArray();
    }

    // === MP4 Builder ===

    public static byte[] buildMP4Atom(String type, byte[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int size = 8 + data.length;
        baos.write(be32(size), 0, 4);
        baos.write(type.getBytes(StandardCharsets.ISO_8859_1), 0, 4);
        baos.write(data, 0, data.length);
        return baos.toByteArray();
    }

    public static byte[] buildMP4MetadataAtom(String type, int dataType, byte[] value) {
        byte[] dataAtomData = concat(be32(dataType), be32(0), value);
        byte[] dataAtom = buildMP4Atom("data", dataAtomData);
        return buildContainer(type, dataAtom);
    }

    public static byte[] buildMP4IlstContainer(byte[]... items) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] item : items) {
            writeBytes(data, item);
        }
        return buildContainer("ilst", data.toByteArray());
    }

    public static byte[] buildContainer(String type, byte[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int size = 8 + data.length;
        baos.write(be32(size), 0, 4);
        baos.write(type.getBytes(StandardCharsets.ISO_8859_1), 0, 4);
        baos.write(data, 0, data.length);
        return baos.toByteArray();
    }

    public static byte[] buildMP4FullFile(byte[] ilstData) {
        byte[] ilstContainer = buildContainer("ilst", ilstData);
        byte[] udtaData = buildContainer("udta", buildContainer("meta", concat(be32(0), ilstContainer)));
        byte[] moovAtom = buildContainer("moov", udtaData);
        return moovAtom;
    }

    // === RIFF/WAV Builder ===

    public static byte[] buildRIFFInfoChunk(String... keyValuePairs) {
        ByteArrayOutputStream subChunks = new ByteArrayOutputStream();
        subChunks.write(ascii("INFO"), 0, 4);
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String id = keyValuePairs[i];
            String value = keyValuePairs[i + 1];
            byte[] valueBytes = concat(value.getBytes(StandardCharsets.UTF_8), new byte[]{0});
            subChunks.write(ascii(id), 0, 4);
            subChunks.write(le32(valueBytes.length), 0, 4);
            subChunks.write(valueBytes, 0, valueBytes.length);
            if (valueBytes.length % 2 != 0) {
                subChunks.write(0);
            }
        }
        byte[] subData = subChunks.toByteArray();
        int totalSize = subData.length;
        byte[] result = new byte[8 + totalSize];
        System.arraycopy(ascii("LIST"), 0, result, 0, 4);
        System.arraycopy(le32(totalSize), 0, result, 4, 4);
        System.arraycopy(subData, 0, result, 8, totalSize);
        return result;
    }

    // === BWF Builder ===

    public static byte[] buildBWFChunk(String description, String originator, String originatorRef,
                                        String date, String time, long timeReference,
                                        int version, byte[] umid, byte[] loudnessData) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeBytes(baos, ascii("bext"));

        byte[] descBytes = new byte[256];
        byte[] origBytes = new byte[32];
        byte[] origRefBytes = new byte[32];
        byte[] dateBytes = new byte[10];
        byte[] timeBytesInner = new byte[8];
        byte[] umidBytesInner = umid != null ? umid : new byte[64];

        if (description != null)
            System.arraycopy(description.getBytes(StandardCharsets.UTF_8), 0, descBytes, 0, Math.min(description.length(), 256));
        if (originator != null)
            System.arraycopy(originator.getBytes(StandardCharsets.UTF_8), 0, origBytes, 0, Math.min(originator.length(), 32));
        if (originatorRef != null)
            System.arraycopy(originatorRef.getBytes(StandardCharsets.UTF_8), 0, origRefBytes, 0, Math.min(originatorRef.length(), 32));
        if (date != null)
            System.arraycopy(date.getBytes(StandardCharsets.UTF_8), 0, dateBytes, 0, Math.min(date.length(), 10));
        if (time != null)
            System.arraycopy(time.getBytes(StandardCharsets.UTF_8), 0, timeBytesInner, 0, Math.min(time.length(), 8));

        int dataSize = 256 + 32 + 32 + 10 + 8 + 8 + 2 + 64 + (version >= 2 ? 180 + 190 : 190);
        writeBytes(baos, le32(dataSize));

        writeBytes(baos, descBytes);
        writeBytes(baos, origBytes);
        writeBytes(baos, origRefBytes);
        writeBytes(baos, dateBytes);
        writeBytes(baos, timeBytesInner);
        writeBytes(baos, le64(timeReference));
        writeBytes(baos, le16(version));
        writeBytes(baos, umidBytesInner);

        if (version >= 2 && loudnessData != null) {
            writeBytes(baos, loudnessData);
            baos.write(new byte[190], 0, 190);
        } else if (version >= 2) {
            baos.write(new byte[370], 0, 370);
        } else {
            baos.write(new byte[190], 0, 190);
        }
        return baos.toByteArray();
    }

    // === ASF Builder ===

    public static byte[] buildASFContentDescObject(String title, String author, String copyright,
                                                    String description, String rating) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{0x33, 0x26, (byte)0xB2, 0x75, (byte)0x8E, 0x66, (byte)0xCF, 0x11,
                (byte)0xA6, (byte)0xD9, 0x00, (byte)0xAA, 0x00, 0x62, (byte)0xCE, 0x6C}, 0, 16);

        byte[] titleBytes = title != null && !title.isEmpty() ? concat(utf16le(title), new byte[]{0, 0}) : new byte[0];
        byte[] authorBytes = author != null && !author.isEmpty() ? concat(utf16le(author), new byte[]{0, 0}) : new byte[0];
        byte[] copyrightBytes = copyright != null && !copyright.isEmpty() ? concat(utf16le(copyright), new byte[]{0, 0}) : new byte[0];
        byte[] descBytesInner = description != null && !description.isEmpty() ? concat(utf16le(description), new byte[]{0, 0}) : new byte[0];
        byte[] ratingBytes = rating != null && !rating.isEmpty() ? concat(utf16le(rating), new byte[]{0, 0}) : new byte[0];

        int dataSize = titleBytes.length + authorBytes.length + copyrightBytes.length + descBytesInner.length + ratingBytes.length;
        int totalSize = 34 + dataSize;
        baos.write(le64(totalSize), 0, 8);
        baos.write(le16(titleBytes.length), 0, 2);
        baos.write(le16(authorBytes.length), 0, 2);
        baos.write(le16(copyrightBytes.length), 0, 2);
        baos.write(le16(descBytesInner.length), 0, 2);
        baos.write(le16(ratingBytes.length), 0, 2);

        if (titleBytes.length > 0) writeBytes(baos, titleBytes);
        if (authorBytes.length > 0) writeBytes(baos, authorBytes);
        if (copyrightBytes.length > 0) writeBytes(baos, copyrightBytes);
        if (descBytesInner.length > 0) writeBytes(baos, descBytesInner);
        if (ratingBytes.length > 0) writeBytes(baos, ratingBytes);

        return baos.toByteArray();
    }

    public static byte[] buildASFExtContentDescObject(ASFAttribute... attrs) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{0x40, (byte)0xA4, (byte)0xD0, (byte)0xD2, 0x07, (byte)0xE3, (byte)0xD2, 0x11,
                (byte)0x97, (byte)0xF0, 0x00, (byte)0xA0, (byte)0xC9, 0x5E, (byte)0xA8, 0x50}, 0, 16);

        int count = attrs.length;
        ByteArrayOutputStream attrData = new ByteArrayOutputStream();
        for (ASFAttribute attr : attrs) {
            byte[] nameBytes = concat(utf16le(attr.name), new byte[]{0, 0});
            attrData.write(le16(nameBytes.length), 0, 2);
            writeBytes(attrData, nameBytes);
            attrData.write(le16(attr.dataType), 0, 2);
            switch (attr.dataType) {
                case 0 -> {
                    byte[] valBytes = concat(utf16le(attr.stringValue), new byte[]{0, 0});
                    attrData.write(le16(valBytes.length), 0, 2);
                    writeBytes(attrData, valBytes);
                }
                case 3 -> {
                    attrData.write(le16(4), 0, 2);
                    attrData.write(le32(attr.intValue), 0, 4);
                }
                case 4 -> {
                    attrData.write(le16(8), 0, 2);
                    attrData.write(le64(attr.longValue), 0, 8);
                }
                case 5 -> {
                    attrData.write(le16(2), 0, 2);
                    attrData.write(le16(attr.intValue), 0, 2);
                }
                case 6 -> {
                    attrData.write(le16(4), 0, 2);
                    attrData.write(le32(attr.boolValue ? 1 : 0), 0, 4);
                }
                default -> {
                    byte[] valBytes = attr.binaryValue;
                    attrData.write(le16(valBytes.length), 0, 2);
                    writeBytes(attrData, valBytes);
                }
            }
        }

        int totalSize = 26 + 2 + attrData.size();
        baos.write(le64(totalSize), 0, 8);
        baos.write(le16(count), 0, 2);
        writeBytes(baos, attrData.toByteArray());

        return baos.toByteArray();
    }

    public record ASFAttribute(String name, int dataType, String stringValue, int intValue,
                               long longValue, boolean boolValue, byte[] binaryValue) {
        public static ASFAttribute string(String name, String value) {
            return new ASFAttribute(name, 0, value, 0, 0, false, null);
        }
        public static ASFAttribute dword(String name, int value) {
            return new ASFAttribute(name, 3, null, value, 0, false, null);
        }
        public static ASFAttribute qword(String name, long value) {
            return new ASFAttribute(name, 4, null, 0, value, false, null);
        }
        public static ASFAttribute word(String name, int value) {
            return new ASFAttribute(name, 5, null, value, 0, false, null);
        }
        public static ASFAttribute bool(String name, boolean value) {
            return new ASFAttribute(name, 6, null, 0, 0, value, null);
        }
    }

    // === DFF Builder ===

    public static byte[] buildDFFDIINChunk(String... keyValuePairs) {
        ByteArrayOutputStream subChunks = new ByteArrayOutputStream();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String chunkId = keyValuePairs[i];
            String value = keyValuePairs[i + 1];
            byte[] valueBytes = utf8(value);
            subChunks.write(chunkId.getBytes(StandardCharsets.US_ASCII), 0, 4);
            subChunks.write(be64(valueBytes.length), 0, 8);
            subChunks.write(valueBytes, 0, valueBytes.length);
            if (valueBytes.length % 2 != 0) {
                subChunks.write(0);
            }
        }
        byte[] subData = subChunks.toByteArray();
        ByteArrayOutputStream diinContainer = new ByteArrayOutputStream();
        diinContainer.write("DIIN".getBytes(StandardCharsets.US_ASCII), 0, 4);
        diinContainer.write(be64(subData.length), 0, 8);
        diinContainer.write(subData, 0, subData.length);
        return diinContainer.toByteArray();
    }

    // === Matroska VLI Builder ===

    public static byte[] buildEBMLVLI(long value) {
        if (value < 0) throw new IllegalArgumentException("VLI value must be non-negative");
        if (value <= 0x7F) return new byte[]{(byte) (0x80 | value)};
        if (value <= 0x3FFF) return new byte[]{(byte) (0x40 | (value >> 8)), (byte) (value & 0xFF)};
        if (value <= 0x1FFFFF) return new byte[]{(byte) (0x20 | (value >> 16)), (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
        if (value <= 0x0FFFFFFF) return new byte[]{(byte) (0x10 | (value >> 24)), (byte) ((value >> 16) & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
        throw new IllegalArgumentException("VLI value too large for test builder");
    }

    public static byte[] buildMatroskaTagsBlock(String... keyValuePairs) {
        ByteArrayOutputStream tagsData = new ByteArrayOutputStream();
        tagsData.write(new byte[]{0x12, 0x54, (byte)0xC3, 0x67}, 0, 4);
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeBytes(content, buildEBMLVLI(0x73C0));
        ByteArrayOutputStream tagContent = new ByteArrayOutputStream();
        ByteArrayOutputStream simpleTagContent = new ByteArrayOutputStream();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            simpleTagContent.reset();
            byte[] nameBytes = keyValuePairs[i].toUpperCase().getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = keyValuePairs[i + 1].getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream nameEBML = new ByteArrayOutputStream();
            nameEBML.write(new byte[]{0x45, (byte)0xA3}, 0, 2);
            writeBytes(nameEBML, buildEBMLVLI(nameBytes.length));
            nameEBML.write(nameBytes, 0, nameBytes.length);
            ByteArrayOutputStream valueEBML = new ByteArrayOutputStream();
            valueEBML.write(new byte[]{0x44, (byte)0x87}, 0, 2);
            writeBytes(valueEBML, buildEBMLVLI(valueBytes.length));
            valueEBML.write(valueBytes, 0, valueBytes.length);
            ByteArrayOutputStream simpleTag = new ByteArrayOutputStream();
            writeBytes(simpleTag, nameEBML.toByteArray());
            writeBytes(simpleTag, valueEBML.toByteArray());
            writeBytes(content, buildEBMLVLI(0x67C8));
            writeBytes(content, buildEBMLVLI(simpleTag.size()));
            writeBytes(content, simpleTag.toByteArray());
        }
        writeBytes(tagContent, content.toByteArray());
        writeBytes(tagsData, buildEBMLVLI(tagContent.size()));
        writeBytes(tagsData, tagContent.toByteArray());
        return tagsData.toByteArray();
    }

    // === DSF Builder ===

    public static byte[] buildDSFID3Chunk(long id3DataSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeBytes(baos, ascii("ID3 "));
        writeBytes(baos, le64(12 + id3DataSize));
        writeBytes(baos, buildID3v2Header(3, 0, (int) id3DataSize - 10));
        return baos.toByteArray();
    }

    // === Lyrics3 Builders ===

    public static byte[] buildLyrics3v1Tag(String lyrics) {
        String content = "LYRICSBEGIN" + lyrics + "LYRICSEND";
        return content.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] buildLyrics3v2Tag(String... fields) {
        StringBuilder content = new StringBuilder("LYRICSBEGIN");
        for (int i = 0; i < fields.length; i += 2) {
            String id = fields[i];
            String value = fields[i + 1];
            if (id.length() != 3) throw new IllegalArgumentException("Lyrics3v2 field ID must be 3 chars: " + id);
            String sizeStr = String.format("%02d", Math.min(value.length(), 99));
            content.append(id).append(sizeStr).append(value, 0, Math.min(value.length(), 99));
        }
        int fieldContentLength = content.length() - 11;
        content.append(String.format("%06d", fieldContentLength));
        content.append("LYRICS200");
        return content.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    // === AIFF Builder ===

    public static byte[] buildAIFFChunk(String chunkType, byte[] chunkData) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ascii(chunkType), 0, 4);
        baos.write(be32(chunkData.length), 0, 4);
        baos.write(chunkData, 0, chunkData.length);
        if (chunkData.length % 2 != 0) {
            baos.write(0);
        }
        return baos.toByteArray();
    }

    public static byte[] buildAIFFTextChunk(String chunkType, String text) {
        byte[] textBytes = concat(utf8(text), new byte[]{0});
        return buildAIFFChunk(chunkType, textBytes);
    }

    // === WavPack Builder ===

    public static byte[] buildWavPackSubBlock(int blockId, byte[] data, boolean large) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (large) {
            baos.write((byte) (blockId | 0x80));
            int size = data.length / 2;
            baos.write(size & 0xFF);
            baos.write((size >> 8) & 0xFF);
            baos.write((size >> 16) & 0xFF);
        } else {
            baos.write((byte) blockId);
            int size = data.length / 2;
            baos.write(size & 0xFF);
        }
        writeBytes(baos, data);
        return baos.toByteArray();
    }

    // === FLAC Application Block Builder ===

    public static byte[] buildFLACApplicationBlock(String appId, byte[] appData, boolean isLast) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int blockType = 2;
        if (isLast) blockType |= 0x80;
        baos.write(blockType);
        int blockLength = 4 + appData.length;
        baos.write((blockLength >> 16) & 0xFF);
        baos.write((blockLength >> 8) & 0xFF);
        baos.write(blockLength & 0xFF);
        baos.write(appId.getBytes(StandardCharsets.US_ASCII), 0, 4);
        writeBytes(baos, appData);
        return baos.toByteArray();
    }
}