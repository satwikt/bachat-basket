package com.twothreads.bachat.listing;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Maps spoken words (English, romanised Hindi/Marathi, Devanagari) to a canonical item. */
public final class SynonymDictionary {

    public record Item(String key, String displayName) {
    }

    private record Entry(String key, String displayName, String... words) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("MILK", "Milk", "milk", "doodh", "dudh", "दूध", "મિલ્ક", "मिल्क", "દૂધ"),
            new Entry("BREAD", "Bread", "bread", "ब्रेड", "બ્રેડ"),
            new Entry("TOMATO", "Tomato", "tomato", "tomatoes", "tamatar", "टमाटर",
                    "ટામેટા", "ટમેટા", "ટામેટું"),
            new Entry("EGG", "Egg", "egg", "eggs", "anda", "अंडा", "ઈંડું", "ઇંડું", "ઈંડા", "ઇંડા"),
            new Entry("CURD", "Curd", "curd", "dahi", "दही", "દહીં"),
            new Entry("PANEER", "Paneer", "paneer", "पनीर", "પનીર"),
            new Entry("ONION", "Onion", "onion", "onions", "pyaz", "कांदा", "ડુંગળી"));

    private final Map<String, Item> byWord;
    private final Set<String> keys;

    public SynonymDictionary() {
        Map<String, Item> map = new HashMap<>();
        Set<String> ks = new TreeSet<>();
        for (Entry entry : ENTRIES) {
            Item item = new Item(entry.key(), entry.displayName());
            ks.add(entry.key());
            for (String word : entry.words()) {
                map.put(word, item);
            }
        }
        this.byWord = Map.copyOf(map);
        this.keys = Set.copyOf(ks);
    }

    public Optional<Item> lookup(String word) {
        if (word == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byWord.get(word.trim().toLowerCase(Locale.ROOT)));
    }

    public Set<String> keys() {
        return keys;
    }
}
