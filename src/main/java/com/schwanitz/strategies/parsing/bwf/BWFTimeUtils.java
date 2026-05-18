package com.schwanitz.strategies.parsing.bwf;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class BWFTimeUtils {

    private BWFTimeUtils() {}

    public static String combineDateTime(String date, String time) {
        try {
            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalDateTime dateTime = LocalDateTime.of(localDate, localTime);
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            return date + " " + time;
        }
    }

    public static String convertTimeReferenceToTimecode(long timeReference, int sampleRate) {
        if (timeReference <= 0 || sampleRate <= 0) {
            return "00:00:00:00";
        }

        double totalSeconds = (double) timeReference / sampleRate;

        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        int frames = (int) ((totalSeconds % 1) * 25);

        return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frames);
    }

    public static boolean isValidDate(String date) {
        return date != null && date.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    public static boolean isValidTime(String time) {
        return time != null && time.matches("\\d{2}:\\d{2}:\\d{2}");
    }
}