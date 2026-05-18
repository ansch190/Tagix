package com.schwanitz.strategies.parsing.unit.bwf;

import com.schwanitz.strategies.parsing.bwf.BWFTimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BWFTimeUtils")
class BWFTimeUtilsTest {

    @Nested
    @DisplayName("combineDateTime")
    class CombineDateTime {

        @Test
        @DisplayName("combines valid date and time")
        void combinesValidDateAndTime() {
            String result = BWFTimeUtils.combineDateTime("2024-06-15", "14:30:00");
            assertEquals("2024-06-15 14:30:00", result);
        }

        @Test
        @DisplayName("returns concatenated string on invalid date")
        void invalidDate() {
            String result = BWFTimeUtils.combineDateTime("not-a-date", "14:30:00");
            assertEquals("not-a-date 14:30:00", result);
        }

        @Test
        @DisplayName("returns concatenated string on invalid time")
        void invalidTime() {
            String result = BWFTimeUtils.combineDateTime("2024-06-15", "not-a-time");
            assertEquals("2024-06-15 not-a-time", result);
        }
    }

    @Nested
    @DisplayName("convertTimeReferenceToTimecode")
    class ConvertTimeReferenceToTimecode {

        @Test
        @DisplayName("converts zero reference to zero timecode")
        void zeroReference() {
            assertEquals("00:00:00:00", BWFTimeUtils.convertTimeReferenceToTimecode(0, 48000));
        }

        @Test
        @DisplayName("converts sample count at 48kHz")
        void convertsAt48k() {
            // 1728000 samples @ 48000 Hz = 36 seconds -> 00:00:36:00 at 25fps
            String result = BWFTimeUtils.convertTimeReferenceToTimecode(1728000, 48000);
            assertEquals("00:00:36:00", result);
        }

        @Test
        @DisplayName("handles negative reference")
        void negativeReference() {
            assertEquals("00:00:00:00", BWFTimeUtils.convertTimeReferenceToTimecode(-1, 48000));
        }

        @Test
        @DisplayName("handles zero sample rate")
        void zeroSampleRate() {
            assertEquals("00:00:00:00", BWFTimeUtils.convertTimeReferenceToTimecode(1000, 0));
        }
    }

    @Nested
    @DisplayName("isValidDate")
    class IsValidDate {

        @Test
        @DisplayName("accepts valid YYYY-MM-DD")
        void validDate() {
            assertTrue(BWFTimeUtils.isValidDate("2024-06-15"));
        }

        @Test
        @DisplayName("rejects invalid format")
        void invalidFormat() {
            assertFalse(BWFTimeUtils.isValidDate("15/06/2024"));
        }

        @Test
        @DisplayName("rejects null")
        void nullDate() {
            assertFalse(BWFTimeUtils.isValidDate(null));
        }
    }

    @Nested
    @DisplayName("isValidTime")
    class IsValidTime {

        @Test
        @DisplayName("accepts valid HH:MM:SS")
        void validTime() {
            assertTrue(BWFTimeUtils.isValidTime("14:30:00"));
        }

        @Test
        @DisplayName("rejects invalid format")
        void invalidFormat() {
            assertFalse(BWFTimeUtils.isValidTime("14:30"));
        }

        @Test
        @DisplayName("rejects null")
        void nullTime() {
            assertFalse(BWFTimeUtils.isValidTime(null));
        }
    }
}