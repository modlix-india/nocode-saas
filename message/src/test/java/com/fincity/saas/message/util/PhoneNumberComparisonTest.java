package com.fincity.saas.message.util;

import static com.fincity.saas.message.util.PhoneUtil.isSameNumber;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The comparison that decides whether an agent's numbers changed.
 *
 * <p>Re-provisioning an agent is the only way to change their virtual or agent number, and this
 * predicate is what decides whether the update is sent to the provider at all. Both ways of being
 * wrong have a cost, and they are not equal: reporting a difference that is not real re-sends an
 * upsert that writes the same values, while reporting a match that is not real means the operator's
 * new number never leaves this service — the request succeeds, the row keeps the old number, and
 * the agent goes on dialling out on it. That second failure is silent, which is why it is pinned
 * here rather than left to a live call.
 *
 * <p>The awkward cases all come from the same source: the provider echoes a number back in
 * whatever shape it stores it, so the value that went out as ten digits can return carrying a
 * country code, a {@code +}, or separators.
 */
class PhoneNumberComparisonTest {

    @Test
    void sameNumberInDifferentShapesIsOneNumber() {

        assertTrue(isSameNumber("+910000000001", "0000000001"), "country code on one side only");
        assertTrue(isSameNumber("910000000001", "+91 00000 00001"), "separators and a plus");
        assertTrue(isSameNumber("0000000001", "0000000001"), "the trivial case still holds");
        assertTrue(isSameNumber("+91-00000-00001", "00000-00001"), "hyphenated either way");
    }

    @Test
    void differentNumbersAreNotConflatedByTheirPrefix() {

        assertFalse(isSameNumber("+910000000001", "+910000000002"), "sharing a country code is not being equal");
        assertFalse(isSameNumber("0000000001", "0000000002"), "last digit differing is a different phone");
    }

    @Test
    void aMissingNumberNeverMatchesOneThatIsPresent() {

        assertFalse(isSameNumber(null, "0000000001"), "nothing provisioned yet is a change, not a match");
        assertFalse(isSameNumber("0000000001", null), "and clearing one is a change too");
        assertFalse(isSameNumber("", "0000000001"), "blank is not a number");
        assertTrue(isSameNumber(null, null), "two absences agree");
    }

    @Test
    void shortValuesFallBackToAnExactCompare() {

        // Extensions and short codes have fewer digits than the last-ten rule can use. Comparing
        // them whole is the only safe reading; taking a suffix of a suffix would match too much.
        assertTrue(isSameNumber("4001", "4001"), "an extension equals itself");
        assertFalse(isSameNumber("4001", "4002"), "and differs from its neighbour");
        assertFalse(isSameNumber("4001", "0000004001"), "a short code is not the number that ends with it");
    }
}
