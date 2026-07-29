package com.modlix.saas.adzump.platform;

/**
 * A discovered ad account (platform-real id). Discovery refs populate the agent's fetched-id set so
 * a plan can only reference ids actually fetched from the connected account this session.
 *
 * @param id       platform account id (Meta {@code act_...}, Google customer id).
 * @param name     human-readable account name.
 * @param currency account currency (ISO code), used when interpreting Money.
 */
public record AdAccountRef(String id, String name, String currency) {
}
