package com.schwanitz.strategies.parsing.bwf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BWFUmidParser {

    private static final Logger LOG = LoggerFactory.getLogger(BWFUmidParser.class);

    private BWFUmidParser() {}

    public static String parseUMID(byte[] umidData) {
        boolean hasContent = false;
        for (byte b : umidData) {
            if (b != 0) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : umidData) {
            sb.append(String.format("%02X", b & 0xFF));
        }

        return sb.toString();
    }

    public static String parseStructuredUMID(byte[] umidData) {
        if (umidData == null || umidData.length != 64) {
            return "";
        }

        boolean hasContent = false;
        for (byte b : umidData) {
            if (b != 0) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            return "";
        }

        try {
            int length = umidData[0] & 0xFF;
            int instance = ((umidData[1] & 0xFF) << 16) | ((umidData[2] & 0xFF) << 8) | (umidData[3] & 0xFF);

            StringBuilder materialNum = new StringBuilder();
            for (int i = 4; i < 20; i++) {
                materialNum.append(String.format("%02X", umidData[i] & 0xFF));
            }

            StringBuilder timestamp = new StringBuilder();
            for (int i = 20; i < 28; i++) {
                timestamp.append(String.format("%02X", umidData[i] & 0xFF));
            }

            return String.format("Length:%d, Instance:%d, Material:%s, Time:%s",
                    length, instance, materialNum, timestamp);

        } catch (Exception e) {
            LOG.debug("Error parsing UMID structure: {}", e.getMessage());
            return "";
        }
    }
}