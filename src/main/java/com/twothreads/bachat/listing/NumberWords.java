package com.twothreads.bachat.listing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns number words (English and Hindi/Marathi) into digits, for the words after the item. */
final class NumberWords {

    private static final Map<String, Integer> VALUES = Map.ofEntries(
            Map.entry("ek", 1), Map.entry("one", 1),
            Map.entry("do", 2), Map.entry("two", 2),
            Map.entry("teen", 3), Map.entry("three", 3),
            Map.entry("char", 4), Map.entry("four", 4),
            Map.entry("paanch", 5), Map.entry("panch", 5), Map.entry("five", 5),
            Map.entry("chhe", 6), Map.entry("six", 6),
            Map.entry("saat", 7), Map.entry("seven", 7),
            Map.entry("aath", 8), Map.entry("eight", 8),
            Map.entry("nau", 9), Map.entry("nine", 9),
            Map.entry("das", 10), Map.entry("ten", 10),
            Map.entry("eleven", 11), Map.entry("twelve", 12), Map.entry("thirteen", 13),
            Map.entry("fourteen", 14), Map.entry("fifteen", 15), Map.entry("sixteen", 16),
            Map.entry("seventeen", 17), Map.entry("eighteen", 18), Map.entry("nineteen", 19),
            Map.entry("bees", 20), Map.entry("twenty", 20),
            Map.entry("thirty", 30), Map.entry("forty", 40), Map.entry("fifty", 50),
            Map.entry("sixty", 60), Map.entry("seventy", 70), Map.entry("eighty", 80),
            Map.entry("ninety", 90));

    private NumberWords() {
    }

    /**
     * Converts number words in a space-separated segment to digits. The first word is never converted,
     * because it is the item name (this protects names such as "do"). A tens word followed by a units
     * word is summed ("twenty three" -> "23").
     */
    static String convert(String segment) {
        String[] tokens = segment.split(" ");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            Integer value = i == 0 ? null : VALUES.get(tokens[i]);
            if (value == null) {
                out.add(tokens[i]);
                continue;
            }
            if (value >= 20 && value % 10 == 0 && i + 1 < tokens.length) {
                Integer units = VALUES.get(tokens[i + 1]);
                if (units != null && units >= 1 && units <= 9) {
                    out.add(String.valueOf(value + units));
                    i++;
                    continue;
                }
            }
            out.add(String.valueOf(value));
        }
        return String.join(" ", out);
    }
}
