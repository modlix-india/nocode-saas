package com.fincity.saas.commons.security.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SecurityContextUtilHasAuthorityTest {

    private static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    private static Collection<SimpleGrantedAuthority> toGrantedAuthorities(List<String> authorities) {
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    // --- Tests with Collection<GrantedAuthority> overload ---

    @Test
    void hasAuthority_withGrantedAuthorities_ownerPresent_shouldReturnTrue() {
        Collection<? extends GrantedAuthority> authorities = toGrantedAuthorities(List.of(
                "Authorities.ROLE_Client_Manager",
                "Authorities.ROLE_Owner",
                "Authorities.User_CREATE"));

        boolean result = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertTrue(result, "Should return true when ROLE_Owner is in the granted authorities");
    }

    @Test
    void hasAuthority_withGrantedAuthorities_ownerAbsent_shouldReturnFalse() {
        Collection<? extends GrantedAuthority> authorities = toGrantedAuthorities(List.of(
                "Authorities.ROLE_Client_Manager",
                "Authorities.User_CREATE"));

        boolean result = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertFalse(result, "Should return false when ROLE_Owner is not in the granted authorities");
    }

    @Test
    void hasAuthority_withGrantedAuthorities_emptyCollection_shouldReturnFalse() {
        Collection<? extends GrantedAuthority> authorities = Set.of();

        boolean result = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertFalse(result, "Should return false for empty authorities collection");
    }

    @Test
    void hasAuthority_withGrantedAuthorities_nullAuthority_shouldReturnTrue() {
        Collection<? extends GrantedAuthority> authorities = toGrantedAuthorities(List.of(
                "Authorities.ROLE_Owner"));

        boolean result = SecurityContextUtil.hasAuthority(null, authorities);
        assertTrue(result, "Should return true when authority to check is null");
    }

    @Test
    void hasAuthority_withGrantedAuthorities_blankAuthority_shouldReturnTrue() {
        Collection<? extends GrantedAuthority> authorities = toGrantedAuthorities(List.of(
                "Authorities.ROLE_Owner"));

        boolean result = SecurityContextUtil.hasAuthority("", authorities);
        assertTrue(result, "Should return true when authority to check is blank");
    }

    // --- Tests with List<String> overload ---

    @Test
    void hasAuthority_withStringList_ownerPresent_shouldReturnTrue() {
        List<String> authorities = List.of(
                "Authorities.ROLE_Client_Manager",
                "Authorities.ROLE_Owner",
                "Authorities.User_CREATE");

        boolean result = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertTrue(result, "Should return true when ROLE_Owner is in the string authorities list");
    }

    @Test
    void hasAuthority_withStringList_ownerAbsent_shouldReturnFalse() {
        List<String> authorities = List.of(
                "Authorities.ROLE_Client_Manager",
                "Authorities.User_CREATE");

        boolean result = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertFalse(result, "Should return false when ROLE_Owner is not in the string authorities list");
    }

    @Test
    void hasAuthority_withStringList_emptyList_shouldReturnFalse() {
        List<String> authorities = List.of();

        boolean result = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertFalse(result, "Should return false for empty authorities list");
    }

    // --- Tests with the full authority list matching the user's actual authorities ---

    @Test
    void hasAuthority_withFullAuthorityList_shouldReturnTrue() {
        List<String> authorities = List.of(
                "Authorities.ROLE_Client_Manager",
                "Authorities.ROLE_User_Manager",
                "Authorities.ROLE_Application_Manager",
                "Authorities.ROLE_Files_Manager",
                "Authorities.ROLE_Data_Manager",
                "Authorities.ROLE_Data_Connection_Manager",
                "Authorities.ROLE_Profile_CREATE",
                "Authorities.ROLE_Profile_READ",
                "Authorities.ROLE_Client_CREATE",
                "Authorities.ROLE_Profile_UPDATE",
                "Authorities.ROLE_Client_READ",
                "Authorities.ROLE_Profile_DELETE",
                "Authorities.ROLE_Client_UPDATE",
                "Authorities.ROLE_Profile_Manager",
                "Authorities.ROLE_Client_DELETE",
                "Authorities.ROLE_Owner",
                "Authorities.ROLE_User_CREATE",
                "Authorities.ROLE_User_READ",
                "Authorities.ROLE_User_UPDATE",
                "Authorities.ROLE_User_DELETE",
                "Authorities.Client_CREATE",
                "Authorities.Client_READ",
                "Authorities.Client_UPDATE",
                "Authorities.Client_DELETE",
                "Authorities.User_CREATE",
                "Authorities.User_READ",
                "Authorities.User_UPDATE",
                "Authorities.User_DELETE",
                "Authorities.Logged_IN");

        boolean resultString = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertTrue(resultString, "Should find ROLE_Owner in full string authority list");

        Collection<? extends GrantedAuthority> grantedAuthorities = toGrantedAuthorities(authorities);
        boolean resultGranted = SecurityContextUtil.hasAuthority(OWNER_ROLE, grantedAuthorities);
        assertTrue(resultGranted, "Should find ROLE_Owner in full granted authority collection");
    }

    // --- Tests for other common authority checks ---

    @Test
    void hasAuthority_userCreate_shouldWork() {
        List<String> authorities = List.of(
                "Authorities.User_CREATE",
                "Authorities.ROLE_Owner",
                "Authorities.Logged_IN");

        boolean result = SecurityContextUtil.hasAuthority("Authorities.User_CREATE", authorities);
        assertTrue(result, "Should find User_CREATE in authorities");
    }

    @Test
    void hasAuthority_withOnlyOwner_shouldReturnTrue() {
        Collection<? extends GrantedAuthority> authorities = toGrantedAuthorities(
                List.of("Authorities.ROLE_Owner"));

        boolean result = SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        assertTrue(result, "Should return true when ROLE_Owner is the only authority");
    }
}