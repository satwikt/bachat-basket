package com.twothreads.bachat.listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser for a spoken listing. No Spring, no database.
 * Each item is spoken as: {@code <item> <qty> [<unit>], original price <N> discounted price <M>}.
 */
public final class VoiceListingParser {

    private static final String DEFAULT_UNIT = "pc";

    /** Currency words and symbols, removed during normalising. */
    private static final Pattern CURRENCY = Pattern.compile(
            "₹|/-|(?<![\\p{L}\\p{M}])rs\\.?(?![\\p{L}\\p{M}])|(?<![\\p{L}\\p{M}])rupees?(?![\\p{L}\\p{M}])");

    /** Separators between segments. Newlines are kept by normalising, so they split here. */
    private static final Pattern SPLIT = Pattern.compile("\\s*[,;\\r\\n]+\\s*|\\s+(?:and|aur|then)\\s+");

    /** A segment that continues the previous one (for example ", original price 30"). */
    /** Words for "original" and "discounted" (English, Gujarati spellings). The template accepts these. */
    private static final String ORIGINAL_WORD =
            "(?:original|ઓરિજનલ|ઓરિજિનલ|ઓરીજનલ|ઓરીજિનલ)";
    private static final String DISCOUNTED_WORD =
            "(?:discounted|discount|offer|ડિસ્કાઉન્ટેડ|ડિસ્કાઉન્ટ|ઓફર)";
    /** "price" in English or Gujarati, optional after either word. */
    private static final String PRICE_WORD = "(?:\\s+(?:price|પ્રાઈસ|કિંમત))?";

    /** A segment that continues the previous one (for example ", original price 30"). */
    private static final Pattern CONTINUATION = Pattern.compile(
            "^(?:" + ORIGINAL_WORD + "|" + DISCOUNTED_WORD + ")(?![\\p{L}\\p{M}])");

    private static final Pattern TEMPLATE = Pattern.compile(
            "^(?<item>[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*?)\\s+(?<qty>\\d+)\\s+(?:(?<unit>[\\p{L}\\p{M}]+)\\s+)?"
                    + ORIGINAL_WORD + PRICE_WORD + "\\s+(?<orig>\\d+(?:\\.\\d+)?)\\s+"
                    + DISCOUNTED_WORD + PRICE_WORD + "\\s+(?<price>\\d+(?:\\.\\d+)?)$");

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("packets", "packet"), Map.entry("pack", "packet"), Map.entry("pkt", "packet"),
            Map.entry("પેકેટ", "packet"),
            Map.entry("kilo", "kg"), Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"),
            Map.entry("કિલો", "kg"),
            Map.entry("gram", "g"), Map.entry("grams", "g"), Map.entry("ગ્રામ", "g"),
            Map.entry("litre", "l"), Map.entry("liter", "l"), Map.entry("ltr", "l"), Map.entry("લિટર", "l"),
            Map.entry("pieces", "pc"), Map.entry("piece", "pc"), Map.entry("nang", "pc"), Map.entry("નંગ", "pc"));

    private final SynonymDictionary dictionary;

    public VoiceListingParser() {
        this(new SynonymDictionary());
    }

    public VoiceListingParser(SynonymDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public List<ParsedLine> parse(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return List.of();
        }
        List<ParsedLine> lines = new ArrayList<>();
        for (String segment : joinContinuations(split(normalise(transcript)))) {
            lines.add(parseSegment(segment));
        }
        return lines;
    }

    /** Step 1: lowercase, drop currency words, collapse spaces and tabs (newlines stay). */
    static String normalise(String transcript) {
        String composed = Normalizer.normalize(transcript.trim(), Normalizer.Form.NFC);
        String lower = composed.toLowerCase(Locale.ROOT);
        String noCurrency = CURRENCY.matcher(lower).replaceAll(" ");
        // speech tolerance: "twenty-three" -> "twenty three"; a filler "is" ("original price is 30") is dropped
        String spaced = noCurrency.replaceAll("(?<=\\p{L})-(?=\\p{L})", " ")
                .replaceAll("(?<![\\p{L}\\p{M}])is(?![\\p{L}\\p{M}])", " ");
        return spaced.replaceAll("[ \\t]+", " ");
    }

    /** Step 2a: split into segments on commas, newlines, "and", "aur", "then". */
    private static List<String> split(String text) {
        return Arrays.stream(SPLIT.split(text))
                .map(s -> s.replaceAll("[.!?।]+$", "").trim()) // speech often ends a phrase with a full stop
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Step 2b: a segment starting with "original", "discounted" etc. belongs to the segment before it. */
    private static List<String> joinContinuations(List<String> segments) {
        List<String> joined = new ArrayList<>();
        for (String segment : segments) {
            if (!joined.isEmpty() && CONTINUATION.matcher(segment).find()) {
                int last = joined.size() - 1;
                joined.set(last, joined.get(last) + " " + segment);
            } else {
                joined.add(segment);
            }
        }
        return joined;
    }

    private ParsedLine parseSegment(String spoken) {
        Matcher m = TEMPLATE.matcher(NumberWords.convert(spoken));
        if (!m.matches()) {
            return unparsed(spoken);
        }

        int quantity;
        try {
            quantity = Integer.parseInt(m.group("qty"));
        } catch (NumberFormatException e) {
            return unparsed(spoken);
        }

        List<Issue> issues = new ArrayList<>();

        String itemWord = m.group("item").trim();
        Optional<SynonymDictionary.Item> item = dictionary.lookup(itemWord);
        String itemKey = null;
        String itemName = itemWord;
        if (item.isPresent()) {
            itemKey = item.get().key();
            itemName = item.get().displayName();
        } else {
            issues.add(Issue.UNKNOWN_ITEM);
        }

        String unit;
        if (m.group("unit") == null) {
            unit = DEFAULT_UNIT;
            issues.add(Issue.NO_UNIT);
        } else {
            unit = UNITS.getOrDefault(m.group("unit"), m.group("unit"));
        }

        if (quantity <= 0) {
            issues.add(Issue.ZERO_QUANTITY);
        }

        BigDecimal offer = money(m.group("price"));
        BigDecimal original = money(m.group("orig"));
        if (offer.signum() <= 0) {
            issues.add(Issue.MISSING_PRICE);
        }
        if (original.signum() <= 0) {
            issues.add(Issue.ORIGINAL_PRICE_MISSING);
        } else if (offer.compareTo(original) >= 0) {
            issues.add(Issue.PRICE_ABOVE_ORIGINAL);
        }

        return new ParsedLine(spoken, itemKey, itemName, quantity, unit, offer, original, issues);
    }

    private static ParsedLine unparsed(String spoken) {
        return new ParsedLine(spoken, null, null, null, null, null, null, List.of(Issue.UNPARSED));
    }

    private static BigDecimal money(String digits) {
        return new BigDecimal(digits).setScale(2, RoundingMode.HALF_UP);
    }
}
