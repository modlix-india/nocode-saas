package com.fincity.saas.entity.processor.conversions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.dto.Ticket;
import org.junit.jupiter.api.Test;

/**
 * Whether a failure is terminal decides whether a conversion is retried or dropped, so the two
 * cases that caused the 2026-10-05 incident are pinned here rather than left to a reading of the
 * code.
 *
 * <p>Getting this wrong is costly in both directions. Too retryable and a permanent fault is
 * re-attempted hourly forever — 60 rows, 36,162 attempts, 98 days, and eventually an outage. Too
 * terminal and a conversion is silently discarded during a transient problem, which nobody notices
 * because the row looks deliberately skipped.
 */
class DispatchResultTerminalTest {

    /** A campaign whose pixel id was never configured — the cause of 49 of the 60 stuck rows. */
    @Test
    void metaWithoutAPixelIdIsTerminal() {

        Campaign campaign = new Campaign();
        campaign.setPlatformDatasetId(null);

        DispatchResult result = new MetaConversionsDispatcher()
                .dispatch(new ConversionEvent(), new ConversionActionMapping(), new Ticket(), campaign, "token")
                .block();

        assertFalse(result.success());
        assertTrue(
                result.terminal(),
                "a missing pixel id is a campaign configuration fault; the platform was already asked"
                        + " for it via ensurePlatformContext and had none, so retrying asks the same"
                        + " question forever");
        assertTrue(result.message().contains("platform_dataset_id"));
    }

    /**
     * The counter-case, and the one most easily got wrong: a missing SERVER property is not bad
     * data. Someone sets it, the service restarts, the next attempt works. Marking it terminal
     * would discard every conversion dispatched during a configuration gap.
     */
    @Test
    void aMissingServerPropertyIsNotTerminal() {

        DispatchResult result =
                DispatchResult.fail("ai.adzump.googleAds.developerToken is not configured", null);

        assertFalse(result.success());
        assertFalse(result.terminal(), "a server-side config gap self-corrects on the next deploy");
    }

    @Test
    void okIsNeverTerminal() {
        DispatchResult ok = DispatchResult.ok("accepted", null);
        assertTrue(ok.success());
        assertFalse(ok.terminal());
    }

    @Test
    void plainFailureStaysRetryable() {
        DispatchResult transientFailure = DispatchResult.fail("Meta 503: upstream unavailable", null);
        assertFalse(transientFailure.success());
        assertFalse(
                transientFailure.terminal(),
                "a 5xx is exactly the case retries exist for and must never be made terminal");
    }
}
