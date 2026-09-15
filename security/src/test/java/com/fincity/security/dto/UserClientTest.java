package com.fincity.security.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UserClient.compareTo orders the sign-in client picker. It existed unused for a
 * long time, so these cover the cases it had never actually met.
 */
class UserClientTest {

    private static UserClient uc(long userId, String clientName) {

        Client c = null;

        if (clientName != null) {
            c = new Client();
            c.setName(clientName);
        }

        return new UserClient().setUserId(ULong.valueOf(userId)).setClient(c);
    }

    private static List<String> namesOf(List<UserClient> list) {

        List<String> names = new ArrayList<>();
        for (UserClient u : list)
            names.add(u.getClient() == null ? null : u.getClient().getName());

        return names;
    }

    @Test
    @DisplayName("Sorts by client name, case-insensitively")
    void sortsByNameIgnoringCase() {

        List<UserClient> list = new ArrayList<>(
                Arrays.asList(uc(1, "zebra"), uc(2, "Apple"), uc(3, "banana")));

        Collections.sort(list);

        assertEquals(Arrays.asList("Apple", "banana", "zebra"), namesOf(list));
    }

    @Test
    @DisplayName("Puts the real picker order right")
    void sortsTheRealWorldCase() {

        List<UserClient> list = new ArrayList<>(Arrays.asList(
                uc(160, "Fincity 2"),
                uc(3778, "Buy Next"),
                uc(218, "Interns of Fincity"),
                uc(110, "Fincity"),
                uc(142, "System Internal")));

        Collections.sort(list);

        assertEquals(
                Arrays.asList("Buy Next", "Fincity", "Fincity 2", "Interns of Fincity", "System Internal"),
                namesOf(list));
    }

    @Test
    @DisplayName("A null client sorts last instead of throwing")
    void nullClientSortsLast() {

        List<UserClient> list = new ArrayList<>(
                Arrays.asList(uc(1, null), uc(2, "Alpha"), uc(3, null), uc(4, "Beta")));

        assertDoesNotThrow(() -> Collections.sort(list));
        assertEquals(Arrays.asList("Alpha", "Beta", null, null), namesOf(list));
    }

    @Test
    @DisplayName("Two nameless entries compare equal, so the sort stays consistent")
    void twoNullsAreEqual() {

        assertEquals(0, uc(1, null).compareTo(uc(2, null)));
        assertTrue(uc(1, "Alpha").compareTo(uc(2, null)) < 0);
        assertTrue(uc(1, null).compareTo(uc(2, "Alpha")) > 0);
    }

    @Test
    @DisplayName("Comparing against null does not throw")
    void nullOtherDoesNotThrow() {

        assertDoesNotThrow(() -> uc(1, "Alpha").compareTo(null));
        assertTrue(uc(1, "Alpha").compareTo(null) < 0);
    }

    @Test
    @DisplayName("Sorting is stable for names differing only by case")
    void sameNameDifferentCaseComparesEqual() {

        assertEquals(0, uc(1, "Fincity").compareTo(uc(2, "FINCITY")));
    }
}
