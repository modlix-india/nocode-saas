package com.modlix.saas.adzump.service.product;

import java.math.BigInteger;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.feign.IFeignEntityProcessorService;
import com.modlix.saas.adzump.model.product.ProductLearnings;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.util.JsonMergePatchUtil;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * J9 - product enhancement. adzump owns NO product table: it <b>references</b> the entity-processor
 * (leadzump) product and <b>enhances</b> it, writing A2's studied ad-relevant profile back onto the
 * product and, over the loop, folding in generalized performance learnings. The write-back transport
 * is J11's {@link IFeignEntityProcessorService#patchProductProfile} which <b>forwards the user's
 * token</b> so entity-processor enforces its OWN product-write authority (adzump cannot write a
 * product the user cannot). The campaign&lt;-&gt;product link lives in entity-processor (J11), not here.
 *
 * <p>Merge semantics are RFC-7386 ({@link JsonMergePatchUtil}): the studied / learnings fields fill
 * ad-relevant gaps over the product's existing profile; CRM-owned keys the patch does not name are
 * preserved (never clobbered). Only the product DEFINITION profile is written - never CRM runtime
 * tickets.
 *
 * <p>Mutating methods carry {@code EDIT} authority and resolve an effective client code (a managing
 * client's Owner or the system client may act for a sub-client), copying the {@code files} idiom used
 * across the adzump services. {@link #isStudied(String)} is a read (no {@code @PreAuthorize}) backing
 * the studied-product guard (J6/J8).
 */
@Service
public class ProductEnhancementService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final String PROFILE = "profile";
    private static final String CLIENT_CODE = "clientCode";
    private static final String LEARNINGS = "learnings";
    private static final String CLIENT = "client";
    private static final String BEARER = "Bearer ";
    private static final String FORWARDED_HOST = "X-Forwarded-Host";
    private static final String FORWARDED_PORT = "X-Forwarded-Port";

    private final IFeignEntityProcessorService feignEntityProcessorService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public ProductEnhancementService(IFeignEntityProcessorService feignEntityProcessorService,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {
        this.feignEntityProcessorService = feignEntityProcessorService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    /**
     * Merges A2's studied ad-relevant profile into the entity-processor product profile (RFC-7386)
     * and writes it back through J11 (user token forwarded). CRM-owned fields the studied profile does
     * not name are preserved. Returns the merged profile that was written.
     */
    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public JsonNode enhanceProfile(String productId, JsonNode studiedProfile, String targetClientCode) {

        if (StringUtil.safeIsBlank(productId))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "productId");

        if (studiedProfile == null || studiedProfile.isNull() || studiedProfile.isEmpty())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "studiedProfile");

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        // Read the current product (forwarded token -> EP enforces read authority) for the merge base
        // and to confirm tenant visibility before we write.
        JsonNode product = this.readProduct(productId, ca, effectiveClient);

        // RFC-7386: studied fields over the existing profile; unnamed CRM-owned keys survive.
        JsonNode mergedPatch = JsonMergePatchUtil.merge(product.get(PROFILE), studiedProfile);

        this.writeProfile(productId, mergedPatch, ca);
        return mergedPatch;
    }

    /**
     * Folds a DE-IDENTIFIED, generalized learnings summary (winning audiences / angles / junk patterns)
     * into the product profile under a dedicated {@code learnings} key - a controlled, explicit write,
     * not raw metrics dumped onto the product (RETRIEVAL no-auto-promotion). Returns the merged profile.
     */
    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public JsonNode foldLearnings(String productId, ProductLearnings learnings, String targetClientCode) {

        if (StringUtil.safeIsBlank(productId))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "productId");

        if (learnings == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, LEARNINGS);

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        JsonNode product = this.readProduct(productId, ca, effectiveClient);

        // A small structured block under "learnings" - null lists omitted (NON_NULL). The fold merges
        // over the existing profile so the studied fields and CRM-owned keys are left intact.
        ObjectNode learningsPatch = MAPPER.createObjectNode();
        learningsPatch.set(LEARNINGS, MAPPER.valueToTree(learnings));

        JsonNode mergedPatch = JsonMergePatchUtil.merge(product.get(PROFILE), learningsPatch);

        this.writeProfile(productId, mergedPatch, ca);
        return mergedPatch;
    }

    /**
     * The studied-product guard read (J6/J8): a product is "studied" once its profile is present and
     * non-empty. Reads the entity-processor product via the forwarded token (EP enforces read
     * authority); an absent product or profile reads as not-studied.
     */
    public boolean isStudied(String productId) {

        if (StringUtil.safeIsBlank(productId))
            return false;

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        JsonNode product = this.getProductOrNull(productId, ca);

        if (product == null)
            return false;

        JsonNode profile = product.get(PROFILE);
        return profile != null && profile.isObject() && !profile.isEmpty();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    // Reads the product (throws PRODUCT_NOT_FOUND when absent) and confirms the product's own client
    // is the effective client or one it manages (FORBIDDEN otherwise). EP also enforces authority on
    // the forwarded token; this is the adzump-side tenant guard.
    private JsonNode readProduct(String productId, ContextAuthentication ca, String effectiveClient) {

        JsonNode product = this.getProductOrNull(productId, ca);

        if (product == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    AdzumpMessageResourceService.PRODUCT_NOT_FOUND, productId);

        String productClientCode = product.hasNonNull(CLIENT_CODE) ? product.get(CLIENT_CODE).asText() : null;
        if (!this.isClientBeingManaged(effectiveClient, productClientCode))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, "product " + productId);

        return product;
    }

    private JsonNode getProductOrNull(String productId, ContextAuthentication ca) {

        String[] hostPort = forwardedHostPort();
        Map<String, Object> raw = this.feignEntityProcessorService.getProduct(
                BEARER + safe(ca.getAccessToken()), clientCodeHeader(ca), appCodeHeader(ca),
                hostPort[0], hostPort[1], productId);

        if (raw == null || raw.isEmpty())
            return null;

        return MAPPER.valueToTree(raw);
    }

    private void writeProfile(String productId, JsonNode mergedPatch, ContextAuthentication ca) {

        String[] hostPort = forwardedHostPort();
        this.feignEntityProcessorService.patchProductProfile(
                BEARER + safe(ca.getAccessToken()), clientCodeHeader(ca), appCodeHeader(ca),
                hostPort[0], hostPort[1], productId, mergedPatch);
    }

    /**
     * The files-service tenant idiom: same client, or a managed one (blocking security feign).
     */
    private boolean isClientBeingManaged(String managingClientCode, String clientCode) {

        if (StringUtil.safeEquals(managingClientCode, clientCode))
            return true;

        return Boolean.TRUE.equals(this.securityService.doesClientManageClientCode(managingClientCode, clientCode));
    }

    /**
     * Resolves the effective client code for a write, mirroring the rule in the other adzump services
     * (and {@code FilesAccessPathService}). Defaults to the caller's own client; a differing target is
     * allowed only for the system client or a managing client administering it.
     */
    private String resolveEffectiveClientCode(String targetClientCode, ContextAuthentication ca) {

        String own = ca.getLoggedInFromClientCode();

        if (targetClientCode == null || targetClientCode.isBlank()
                || StringUtil.safeEquals(targetClientCode.trim(), own))
            return own;

        String target = targetClientCode.trim();
        BigInteger targetClientId = this.securityService.getClientIdByCode(target);

        boolean allowed = ca.isSystemClient()
                || Boolean.TRUE.equals(this.securityService.isUserClientManageClient(ca.getUrlAppCode(),
                        ca.getUser().getId(), ca.getUser().getClientId(), targetClientId));

        if (!allowed)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CLIENT);

        return target;
    }

    // The J11 forwarded header set: Authorization (Bearer) + clientCode + appCode come from the
    // caller's ContextAuthentication; X-Forwarded-Host/Port come from the inbound request so EP
    // rebuilds the same ContextAuthentication. Outside a request thread (e.g. a future scheduled
    // loop) host/port are empty - the token still carries the identity.
    private static String[] forwardedHostPort() {

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {

            HttpServletRequest request = servletAttrs.getRequest();

            String host = request.getHeader(FORWARDED_HOST);
            if (host == null || host.isBlank())
                host = request.getServerName();

            String port = request.getHeader(FORWARDED_PORT);
            if (port == null || port.isBlank())
                port = String.valueOf(request.getServerPort());

            return new String[] { safe(host), safe(port) };
        }

        return new String[] { "", "" };
    }

    private static String clientCodeHeader(ContextAuthentication ca) {
        String urlClientCode = ca.getUrlClientCode();
        return urlClientCode == null || urlClientCode.isBlank() ? safe(ca.getClientCode()) : urlClientCode;
    }

    private static String appCodeHeader(ContextAuthentication ca) {
        return safe(ca.getUrlAppCode());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
