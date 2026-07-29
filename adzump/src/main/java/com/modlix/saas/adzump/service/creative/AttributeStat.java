package com.modlix.saas.adzump.service.creative;

/**
 * The learned standing of a single attribute value (e.g. {@code angle=investment_roi}) in the account's
 * attribute performance map (J20 §5.2). This is the decomposition of realized creative outcomes onto
 * the J5 taxonomy — "which angle/scene/offer wins", with the evidence quantified so the loop does not
 * over-trust a thin or junk-correlated signal.
 *
 * @param axis             the taxonomy axis (angle, scene, offer, cta, ...).
 * @param value            the value on that axis.
 * @param regularizedScore the shrunk 0..100 performance (a regularized lift, NOT a naive mean): the
 *                         volume-weighted observed mean pulled toward the prior/baseline by a
 *                         pseudo-count, so an attribute seen on little data does not swing to an extreme.
 * @param baseline         the account (or cold-start prior) baseline the lift is measured against.
 * @param lift             {@code regularizedScore - baseline} — points above/below an average creative.
 * @param volume           total CRM outcome volume behind this value.
 * @param creativeCount    how many distinct creatives carried this value (breadth of evidence).
 * @param confidence        0..1 trust in the standing: grows with volume AND breadth, so a value proven
 *                          by one high-volume creative is still low-confidence (§8 "seen on 2 creatives
 *                          isn't proven").
 * @param junkCorrelation  how much junkier this value's leads are than the account average (leadzump
 *                         junk signal, roughly -1..1; positive = worse), a penalty the loop applies.
 * @param winner           true iff the value clears the exploit gate (confident, enough breadth, real
 *                         positive lift, not junk-correlated).
 * @param underExplored    true iff the value has too little evidence to exploit yet (an explore target
 *                         for A4/J21).
 */
public record AttributeStat(
        String axis,
        String value,
        double regularizedScore,
        double baseline,
        double lift,
        long volume,
        int creativeCount,
        double confidence,
        double junkCorrelation,
        boolean winner,
        boolean underExplored) {
}
