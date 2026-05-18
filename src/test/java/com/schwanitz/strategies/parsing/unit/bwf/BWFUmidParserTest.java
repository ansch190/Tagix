package com.schwanitz.strategies.parsing.unit.bwf;

import com.schwanitz.strategies.parsing.bwf.BWFUmidParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BWFUmidParser")
class BWFUmidParserTest {

    @Nested
    @DisplayName("parseUMID")
    class ParseUMID {

        @Test
        @DisplayName("returns empty string for all-zero UMID")
        void allZeroUmid() {
            byte[] umidData = new byte[64];
            assertEquals("", BWFUmidParser.parseUMID(umidData));
        }

        @Test
        @DisplayName("returns hex string for non-zero UMID")
        void nonZeroUmid() {
            byte[] umidData = new byte[64];
            umidData[0] = (byte) 0xAB;
            umidData[1] = (byte) 0xCD;
            String result = BWFUmidParser.parseUMID(umidData);
            assertTrue(result.startsWith("ABCD"));
            assertEquals(128, result.length()); // 64 bytes * 2 hex chars
        }
    }

    @Nested
    @DisplayName("parseStructuredUMID")
    class ParseStructuredUMID {

        @Test
        @DisplayName("returns empty string for null input")
        void nullInput() {
            assertEquals("", BWFUmidParser.parseStructuredUMID(null));
        }

        @Test
        @DisplayName("returns empty string for wrong size")
        void wrongSize() {
            assertEquals("", BWFUmidParser.parseStructuredUMID(new byte[32]));
        }

        @Test
        @DisplayName("returns empty string for all-zero UMID")
        void allZeroUmid() {
            assertEquals("", BWFUmidParser.parseStructuredUMID(new byte[64]));
        }

        @Test
        @DisplayName("parses structured UMID with content")
        void parsesStructuredUmid() {
            byte[] umidData = new byte[64];
            umidData[0] = 0x0C; // length
            umidData[1] = 0x00;
            umidData[2] = 0x01;
            umidData[3] = 0x02;
            umidData[4] = (byte) 0xFF;
            umidData[20] = 0x01;

            String result = BWFUmidParser.parseStructuredUMID(umidData);
            assertTrue(result.startsWith("Length:12"));
            assertTrue(result.contains("Instance:258")); // 0x0102 = 258
            assertTrue(result.contains("Material:"));
        }
    }
}