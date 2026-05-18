package com.schwanitz.strategies.parsing.unit.bwf;

import com.schwanitz.strategies.parsing.bwf.BWFCodingHistoryParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BWFCodingHistoryParser")
class BWFCodingHistoryParserTest {

    @Nested
    @DisplayName("parseStructuredCodingHistory")
    class ParseStructuredCodingHistory {

        @Test
        @DisplayName("returns empty string for null input")
        void nullInput() {
            assertEquals("", BWFCodingHistoryParser.parseStructuredCodingHistory(null));
        }

        @Test
        @DisplayName("returns empty string for empty input")
        void emptyInput() {
            assertEquals("", BWFCodingHistoryParser.parseStructuredCodingHistory(""));
        }

        @Test
        @DisplayName("parses single coding step")
        void singleStep() {
            String history = "A=PCM,F=48000,W=24,M=stereo,T=Original Recording";
            String result = BWFCodingHistoryParser.parseStructuredCodingHistory(history);
            assertTrue(result.contains("Algorithm=PCM"));
            assertTrue(result.contains("SampleRate=48000Hz"));
            assertTrue(result.contains("WordLength=24bit"));
            assertTrue(result.contains("Mode=stereo"));
            assertTrue(result.contains("Text=\"Original Recording\""));
        }

        @Test
        @DisplayName("parses multiple coding steps separated by newlines")
        void multipleSteps() {
            String history = "A=PCM,F=48000,W=24,M=stereo,T=Original\nA=MP3,F=44100,W=16,M=joint stereo,T=Encoded";
            String result = BWFCodingHistoryParser.parseStructuredCodingHistory(history);
            assertTrue(result.contains("Step:"));
            assertTrue(result.contains(";"));
            assertTrue(result.contains("Algorithm=PCM"));
            assertTrue(result.contains("Algorithm=MP3"));
        }

        @Test
        @DisplayName("preserves unstructured lines")
        void unstructuredLine() {
            String history = "Some free text description";
            String result = BWFCodingHistoryParser.parseStructuredCodingHistory(history);
            assertTrue(result.contains("Step: Some free text description"));
        }

        @Test
        @DisplayName("handles partial key=value pairs")
        void partialKeyValue() {
            String history = "A=PCM,F=48000";
            String result = BWFCodingHistoryParser.parseStructuredCodingHistory(history);
            assertTrue(result.contains("Algorithm=PCM"));
            assertTrue(result.contains("SampleRate=48000Hz"));
            assertFalse(result.contains("WordLength="));
        }
    }
}