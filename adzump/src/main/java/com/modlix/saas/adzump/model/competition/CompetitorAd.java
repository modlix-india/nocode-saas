package com.modlix.saas.adzump.model.competition;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One competitor/market ad as the Meta Ad Library ({@code ads_archive}) surfaces it, normalized into
 * adzump's own shape (no Graph type leaks up). This is what {@link com.modlix.saas.adzump.service.competition.AdLibraryClient}
 * returns and what J19 ranks + persists; A4/vision later enriches it (theme, attributes) from the
 * stored creative.
 *
 * <p><b>What the Ad Library gives us — and does not (J19 §5.1).</b> Per ad it returns the creative
 * (text + a snapshot image), the advertiser {@link #pageId}/{@link #pageName}, the delivery
 * {@link #deliveryStart} (and {@link #deliveryStop}, null while still running), and {@link #adSnapshotUrl}.
 * It returns {@link #reach} <b>only for political/issue ads</b> in most regions; for commercial ads
 * there is no spend, no impressions, no CTR and no conversions — so {@code reach} is usually null and
 * the proxy must never require it.
 *
 * @see com.modlix.saas.adzump.service.competition.BestWorkingProxy
 */
@Data
@Accessors(chain = true)
public class CompetitorAd implements Serializable {

    @Serial
    private static final long serialVersionUID = 7420193857610293841L;

    /** Ad Library archive id (may be null when absent from the response); used for exact de-dup. */
    private String id;

    /** Advertiser page id — the identity iteration density (same advertiser) is grouped on. */
    private String pageId;

    /** Advertiser page (brand) name, for display. */
    private String pageName;

    /** Primary creative text (first non-empty {@code ad_creative_bodies}). */
    private String creativeBody;

    /** Creative headline/title (first non-empty {@code ad_creative_link_titles}), when present. */
    private String creativeTitle;

    /** Snapshot image URL from the Ad Library, when present (source for J16 asset storage later). */
    private String creativeImageUrl;

    /** Modlix files ref once the creative is stored as a J16 asset; null at research time. */
    private String creativeImageRef;

    /** The Ad Library {@code ad_snapshot_url} (the canonical human-viewable ad page). */
    private String adSnapshotUrl;

    /** Continuous-run start (the anchor for longevity). */
    private LocalDate deliveryStart;

    /** Continuous-run stop; <b>null means still running</b> (drives recency + open-ended longevity). */
    private LocalDate deliveryStop;

    /**
     * Reach/impressions proxy — <b>present only for political/issue ads</b> (else null). The proxy uses
     * it only when non-null and never penalizes its absence (J19 §5.2).
     */
    private Long reach;

    /**
     * A theme/offer key that groups an advertiser's near-variant re-cuts (iteration density) and the
     * same theme across competitors (breadth). Set by A4/vision later; null at research time, in which
     * case {@link com.modlix.saas.adzump.service.competition.BestWorkingProxy} falls back to a coarse
     * fingerprint of {@link #creativeBody}.
     */
    private String themeKey;

    /** The platform this ad came from (META for the Ad Library). */
    private Platform platform;
}
