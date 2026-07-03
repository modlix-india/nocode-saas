package com.modlix.saas.adzump.platform;

/**
 * A discovered keyword idea returned by {@link AdPlatform#searchKeywords} (Google). Meta returns an
 * empty list (capabilities-gated). P1 keeps it lean: the term plus its average monthly search
 * volume; competition / bid-range fields arrive with the J4 Google slice.
 *
 * @param text                keyword text.
 * @param avgMonthlySearches  average monthly search volume, or null when the platform omits it.
 */
public record KeywordRef(String text, Long avgMonthlySearches) {
}
