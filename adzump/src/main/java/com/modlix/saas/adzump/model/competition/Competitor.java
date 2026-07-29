package com.modlix.saas.adzump.model.competition;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One competitor to mine the Ad Library for — an advertiser identified by its Meta {@code pageId}
 * (with an optional human name). The competitor list is an <b>input</b> to J19: A2's competitor
 * discovery produces it (J19 §2). Here it is passed in / mocked; A4 wiring lands later.
 */
@Data
@Accessors(chain = true)
public class Competitor implements Serializable {

    @Serial
    private static final long serialVersionUID = 6510293847610293841L;

    /** Meta advertiser page id whose running ads are fetched via {@code search_page_ids}. */
    private String pageId;

    /** Advertiser/brand name, for display and de-dup readability. */
    private String pageName;
}
