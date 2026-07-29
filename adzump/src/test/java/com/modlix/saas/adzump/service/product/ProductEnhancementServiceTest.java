package com.modlix.saas.adzump.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.feign.IFeignEntityProcessorService;
import com.modlix.saas.adzump.model.product.ProductLearnings;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * J9 product-enhancement unit tests, entirely offline: the J11 entity-processor Feign is mocked
 * (Mockito) - no network, no live EP. Uses a real {@link AdzumpMessageResourceService} so tenant /
 * validation failures raise real {@link GenericException}s. Covers the P2 exit for J9: the studied
 * profile is merged (not clobbered) and written back with the user token forwarded, the studied-guard
 * flips once a profile is present, EP write rejection surfaces, and the effective-client tenant gate
 * denies a foreign client.
 */
class ProductEnhancementServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();
    private static final String PRODUCT_ID = "prd-1";
    private static final String OWN_CLIENT = "CLI0";

    private IFeignEntityProcessorService feign;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;
    private ProductEnhancementService service;

    @BeforeEach
    void setUp() {
        this.feign = mock(IFeignEntityProcessorService.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn(OWN_CLIENT);

        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);

        this.service = new ProductEnhancementService(this.feign, this.security, MSG);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    // =====================================================================================
    // Tests
    // =====================================================================================

    @Test
    void enhanceProfile_merge_preservesCrmOwnedFields_onlyAdRelevantKeysChange() {

        // Existing EP profile carries CRM-owned fields plus a stale ad-relevant field.
        Map<String, Object> currentProfile = new HashMap<>();
        currentProfile.put("crmOwnerId", "crm-123"); // CRM-owned
        currentProfile.put("leadRouting", "round-robin"); // CRM-owned
        currentProfile.put("pitch", "old pitch"); // ad-relevant, pre-existing
        when(this.feign.getProduct(anyString(), anyString(), anyString(), anyString(), anyString(), eq(PRODUCT_ID)))
                .thenReturn(productWith(currentProfile));

        // A2's studied profile only names ad-relevant keys.
        ObjectNode studied = MAPPER.createObjectNode();
        studied.put("pitch", "new pitch");
        studied.put("tone", "premium");
        studied.set("valueProps", MAPPER.createArrayNode().add("gym").add("clubhouse"));

        JsonNode merged = this.service.enhanceProfile(PRODUCT_ID, studied, null);

        // CRM-owned fields untouched.
        assertEquals("crm-123", merged.get("crmOwnerId").asText());
        assertEquals("round-robin", merged.get("leadRouting").asText());
        // Ad-relevant fields updated / added.
        assertEquals("new pitch", merged.get("pitch").asText());
        assertEquals("premium", merged.get("tone").asText());
        assertEquals(2, merged.get("valueProps").size());

        // The same merged profile is what was written back to EP (never a bare overwrite).
        ArgumentCaptor<JsonNode> patchCap = ArgumentCaptor.forClass(JsonNode.class);
        verify(this.feign).patchProductProfile(anyString(), anyString(), anyString(), anyString(), anyString(),
                eq(PRODUCT_ID), patchCap.capture());
        assertEquals("crm-123", patchCap.getValue().get("crmOwnerId").asText());
        assertEquals("new pitch", patchCap.getValue().get("pitch").asText());
    }

    @Test
    void enhanceProfile_write_forwardsUserToken() {

        when(this.ca.getAccessToken()).thenReturn("jwt-abc");
        when(this.feign.getProduct(anyString(), anyString(), anyString(), anyString(), anyString(), eq(PRODUCT_ID)))
                .thenReturn(productWith(Map.of("pitch", "x")));

        ObjectNode studied = MAPPER.createObjectNode();
        studied.put("pitch", "y");

        this.service.enhanceProfile(PRODUCT_ID, studied, null);

        // The write forwards the caller's own bearer token so EP enforces its own write authority.
        ArgumentCaptor<String> authCap = ArgumentCaptor.forClass(String.class);
        verify(this.feign).patchProductProfile(authCap.capture(), anyString(), anyString(), anyString(), anyString(),
                eq(PRODUCT_ID), any(JsonNode.class));
        assertEquals("Bearer jwt-abc", authCap.getValue());

        // ...and so does the read that fetched the merge base.
        ArgumentCaptor<String> readAuthCap = ArgumentCaptor.forClass(String.class);
        verify(this.feign).getProduct(readAuthCap.capture(), anyString(), anyString(), anyString(), anyString(),
                eq(PRODUCT_ID));
        assertEquals("Bearer jwt-abc", readAuthCap.getValue());
    }

    @Test
    void isStudied_flipsOnceProfilePresent() {

        // First read has no profile -> not studied; second read has a profile -> studied.
        when(this.feign.getProduct(anyString(), anyString(), anyString(), anyString(), anyString(), eq(PRODUCT_ID)))
                .thenReturn(productWith(null), productWith(Map.of("pitch", "studied pitch")));

        assertFalse(this.service.isStudied(PRODUCT_ID));
        assertTrue(this.service.isStudied(PRODUCT_ID));
    }

    @Test
    void enhanceProfile_unauthorizedProductWrite_surfacesEpError() {

        // The read is allowed, but EP rejects the write on the forwarded token.
        when(this.feign.getProduct(anyString(), anyString(), anyString(), anyString(), anyString(), eq(PRODUCT_ID)))
                .thenReturn(productWith(Map.of("pitch", "x")));
        doThrow(new GenericException(HttpStatus.FORBIDDEN, "entity-processor: not allowed to write this product"))
                .when(this.feign).patchProductProfile(anyString(), anyString(), anyString(), anyString(), anyString(),
                        eq(PRODUCT_ID), any(JsonNode.class));

        ObjectNode studied = MAPPER.createObjectNode();
        studied.put("pitch", "y");

        GenericException ex = assertThrows(GenericException.class,
                () -> this.service.enhanceProfile(PRODUCT_ID, studied, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void foldLearnings_foldsDeIdentifiedSummary_preservingCrmAndStudiedFields() {

        Map<String, Object> currentProfile = new HashMap<>();
        currentProfile.put("crmOwnerId", "crm-1"); // CRM-owned
        currentProfile.put("pitch", "studied pitch"); // studied, must survive
        when(this.feign.getProduct(anyString(), anyString(), anyString(), anyString(), anyString(), eq(PRODUCT_ID)))
                .thenReturn(productWith(currentProfile));

        ProductLearnings learnings = new ProductLearnings(
                List.of("investors 35-50"), List.of("possession-soon"), List.of("budget-mismatch"));

        JsonNode merged = this.service.foldLearnings(PRODUCT_ID, learnings, null);

        assertEquals("crm-1", merged.get("crmOwnerId").asText());
        assertEquals("studied pitch", merged.get("pitch").asText());

        JsonNode block = merged.get("learnings");
        assertEquals("investors 35-50", block.get("winningAudiences").get(0).asText());
        assertEquals("possession-soon", block.get("winningAngles").get(0).asText());
        assertEquals("budget-mismatch", block.get("junkPatterns").get(0).asText());
    }

    @Test
    void enhanceProfile_foreignClientCallerCannotManage_forbidden_noFeignCall() {

        ContextUser user = mock(ContextUser.class);
        when(user.getId()).thenReturn(BigInteger.valueOf(7));
        when(user.getClientId()).thenReturn(BigInteger.ONE);
        when(this.ca.getUser()).thenReturn(user);
        when(this.ca.getUrlAppCode()).thenReturn("adzump");
        when(this.ca.isSystemClient()).thenReturn(false);
        when(this.security.getClientIdByCode("OTHER")).thenReturn(BigInteger.TEN);
        when(this.security.isUserClientManageClient(eq("adzump"), any(), any(), eq(BigInteger.TEN)))
                .thenReturn(false);

        ObjectNode studied = MAPPER.createObjectNode();
        studied.put("pitch", "y");

        assertThrows(GenericException.class, () -> this.service.enhanceProfile(PRODUCT_ID, studied, "OTHER"));

        // Denied at effective-client resolution, before EP is ever touched.
        verify(this.feign, never()).getProduct(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString());
        verify(this.feign, never()).patchProductProfile(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(JsonNode.class));
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private static Map<String, Object> productWith(Map<String, Object> profile) {
        Map<String, Object> product = new HashMap<>();
        product.put("id", PRODUCT_ID);
        product.put("clientCode", OWN_CLIENT);
        if (profile != null)
            product.put("profile", profile);
        return product;
    }
}
