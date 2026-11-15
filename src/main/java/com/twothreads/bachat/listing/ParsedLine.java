package com.twothreads.bachat.listing;

import java.math.BigDecimal;
import java.util.List;

/**
 * One line of the merchant's listing as the parser read it.
 * For an UNPARSED line only {@code rawPhrase} and {@code issues} are set.
 */
public record ParsedLine(
        String rawPhrase,
        String itemKey,
        String itemName,
        Integer quantity,
        String unit,
        BigDecimal offerPrice,
        BigDecimal originalPrice,
        List<Issue> issues) {

    public ParsedLine {
        issues = List.copyOf(issues);
    }

    public boolean hasBlockingIssue() {
        return issues.stream().anyMatch(Issue::blocking);
    }
}
