package com.schwanitz.strategies.parsing.bwf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BWFCodingHistoryParser {

    private BWFCodingHistoryParser() {}

    public static String parseStructuredCodingHistory(String codingHistory) {
        if (codingHistory == null || codingHistory.isEmpty()) {
            return "";
        }

        List<String> steps = new ArrayList<>();

        String[] lines = codingHistory.split("[\r\n]+");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Map<String, String> parameters = new HashMap<>();

            String[] parts = line.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("=")) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2) {
                        parameters.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }

            if (!parameters.isEmpty()) {
                StringBuilder stepInfo = new StringBuilder();
                stepInfo.append("Step: ");

                if (parameters.containsKey("A")) stepInfo.append("Algorithm=").append(parameters.get("A")).append(" ");
                if (parameters.containsKey("F")) stepInfo.append("SampleRate=").append(parameters.get("F")).append("Hz ");
                if (parameters.containsKey("W")) stepInfo.append("WordLength=").append(parameters.get("W")).append("bit ");
                if (parameters.containsKey("M")) stepInfo.append("Mode=").append(parameters.get("M")).append(" ");
                if (parameters.containsKey("T")) stepInfo.append("Text=\"").append(parameters.get("T")).append("\"");

                steps.add(stepInfo.toString().trim());
            } else if (!line.isEmpty()) {
                steps.add("Step: " + line);
            }
        }

        return String.join("; ", steps);
    }
}