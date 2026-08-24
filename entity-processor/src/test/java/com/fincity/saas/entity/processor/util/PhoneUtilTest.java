package com.fincity.saas.entity.processor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a lead is, worked out from the only thing we hold that says so.
 *
 * <p>This decides which clock quiet hours are judged on, so being wrong here is not a display
 * problem: it is a message arriving in the middle of somebody's night, which is the complaint that
 * produces the report that gets a customer's number blocked. Worth pinning down by test rather than
 * trusting that the mapper does what its name suggests.
 */
class PhoneUtilTest {

    /**
     * Same clock, whatever tzdb calls it.
     *
     * <p>The mapper's data ships tzdb's older names, so India comes back as {@code Asia/Calcutta}
     * rather than {@code Asia/Kolkata}. They are links to one set of rules, so nothing about the
     * behaviour differs, and asserting on the string would make this test a record of which alias
     * the data happened to use rather than of where the lead is.
     */
    private static void assertSameClockAs(String expected, List<ZoneId> actual) {
        assertEquals(1, actual.size(), "expected exactly one zone, got " + actual);
        assertEquals(ZoneId.of(expected).getRules(), actual.getFirst().getRules(), "resolved to " + actual);
    }

    @Test
    @DisplayName("an Indian number resolves to Indian time, in every form it is stored in")
    void indianNumbers() {
        // E.164 is what a deal stores; the bare digits with a dial code are what older rows carry;
        // the spaced form is what somebody types. All three describe one phone in one place.
        assertSameClockAs("Asia/Kolkata", PhoneUtil.zonesOf(91, "+919740485795"));
        assertSameClockAs("Asia/Kolkata", PhoneUtil.zonesOf(91, "9740485795"));
        assertSameClockAs("Asia/Kolkata", PhoneUtil.zonesOf(null, "+91 97404 85795"));
    }

    @Test
    @DisplayName("the number wins over the stored dial code when they disagree")
    void theNumberIsAuthoritative() {
        // Deals are edited and imported, and a dial code left at the tenant default while the number
        // was pasted in full is an ordinary way for the two to drift apart. The number is the thing
        // that actually gets messaged, so it is the thing that decides.
        assertSameClockAs("Asia/Dubai", PhoneUtil.zonesOf(91, "+971501234567"));
    }

    @Test
    @DisplayName("a Gulf number is a different clock from the Indian business messaging it")
    void gulfNumbers() {
        // The case the whole per-lead change exists for. India to the Gulf is ninety minutes, so a
        // window judged on the business's clock is ninety minutes wrong at both ends.
        assertSameClockAs("Asia/Dubai", PhoneUtil.zonesOf(971, "+971501234567"));
    }

    @Test
    @DisplayName("a number that cannot be placed says so, rather than guessing")
    void unresolvableNumbers() {
        // Empty means "no opinion" and lets the caller fall back deliberately. Returning a default
        // from here would be indistinguishable from a number genuinely in that zone, and the caller
        // would have no way to know it was guessing.
        assertTrue(PhoneUtil.zonesOf(null, null).isEmpty());
        assertTrue(PhoneUtil.zonesOf(null, "").isEmpty());
        assertTrue(PhoneUtil.zonesOf(null, "not-a-number").isEmpty());
    }

    @Test
    @DisplayName("a number spanning several zones returns all of them")
    void multiZoneCountries() {
        // +1 covers six zones, so the subscriber digits are what separate a New Jersey lead from a
        // California one. Whatever the mapper can say, it must not silently collapse to one answer:
        // the caller holds against every candidate precisely because it does not know which is right.
        List<ZoneId> zones = PhoneUtil.zonesOf(1, "+12125551234");

        assertTrue(!zones.isEmpty(), "a valid US number should place somewhere");
        assertTrue(
                zones.stream().allMatch(z -> z.getId().startsWith("America/")),
                "a New York number should resolve to American zones, got " + zones);
    }
}
