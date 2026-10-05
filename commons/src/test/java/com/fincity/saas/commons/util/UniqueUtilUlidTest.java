package com.fincity.saas.commons.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class UniqueUtilUlidTest {

    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Test
    void isTwentySixCrockfordCharacters() {
        String id = UniqueUtil.ulid();
        assertEquals(26, id.length(), "a ULID is 26 characters");
        for (char c : id.toCharArray())
            assertTrue(CROCKFORD.indexOf(c) >= 0, "unexpected character '" + c + "' in " + id);
    }

    @Test
    void excludesTheAmbiguousLetters() {
        // I, L, O and U are deliberately absent so an id cannot be misread or mistyped.
        String joined = String.join("", List.of(UniqueUtil.ulid(), UniqueUtil.ulid(), UniqueUtil.ulid()));
        for (char c : new char[] {'I', 'L', 'O', 'U'})
            assertTrue(joined.indexOf(c) < 0, "Crockford base32 must not contain " + c);
    }

    @Test
    void isUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) seen.add(UniqueUtil.ulid());
        assertEquals(10_000, seen.size(), "10k ULIDs should not collide");
    }

    @Test
    void sortsByCreationTime() throws InterruptedException {
        String earlier = UniqueUtil.ulid();
        Thread.sleep(2);
        String later = UniqueUtil.ulid();

        // The point of a ULID over a UUID: string order is time order, so an index on
        // the primary key stays append-friendly and "newest first" needs no extra column.
        assertTrue(earlier.compareTo(later) < 0, earlier + " should sort before " + later);
    }

    @Test
    void sharesThePrefixWithinTheSameMillisecond() {
        // The first 10 chars are the timestamp. Two ids minted together agree on it,
        // which is what makes the random tail load-bearing for uniqueness.
        String a = UniqueUtil.ulid();
        String b = UniqueUtil.ulid();
        if (a.substring(0, 10).equals(b.substring(0, 10)))
            assertTrue(!a.substring(10).equals(b.substring(10)), "random tails must differ");
    }
}
