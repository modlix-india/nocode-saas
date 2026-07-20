package com.modlix.saas.adzump.model.competition;

import java.util.List;

/**
 * Query parameters for an {@code ads_archive} search — the knobs {@link com.modlix.saas.adzump.service.competition.AdLibraryClient}
 * turns into request params (and paging bounds). Kept as a small value so the client stays a thin
 * facade and the service (or a test) controls scope explicitly.
 *
 * @param reachedCountries {@code ad_reached_countries} (required by the API); the market to search.
 * @param activeStatus     {@code ad_active_status} — {@code "ACTIVE"} for running ads (J19 mines running ads), or {@code "ALL"}.
 * @param pageSize         {@code limit} per page.
 * @param maxPages         paging safety bound — stop after this many pages regardless of {@code after} cursor.
 */
public record AdLibraryQuery(
        List<String> reachedCountries,
        String activeStatus,
        int pageSize,
        int maxPages) {

    public static final String ACTIVE = "ACTIVE";
    public static final String ALL = "ALL";

    public AdLibraryQuery {
        reachedCountries = reachedCountries == null ? List.of() : List.copyOf(reachedCountries);
        if (activeStatus == null || activeStatus.isBlank())
            activeStatus = ACTIVE;
        if (pageSize <= 0)
            pageSize = 50;
        if (maxPages <= 0)
            maxPages = 10;
    }

    /** Running ads in the given market, sensible paging bounds. */
    public static AdLibraryQuery runningIn(List<String> reachedCountries) {
        return new AdLibraryQuery(reachedCountries, ACTIVE, 50, 10);
    }
}
