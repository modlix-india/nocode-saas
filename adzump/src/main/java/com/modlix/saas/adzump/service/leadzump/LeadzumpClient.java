package com.modlix.saas.adzump.service.leadzump;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.feign.IFeignEntityProcessorService;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.CrmOutcomes;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.leadzump.OutcomeQuery;
import com.modlix.saas.adzump.model.leadzump.OutcomeRow;
import com.modlix.saas.adzump.model.leadzump.Product;
import com.modlix.saas.adzump.model.leadzump.ProductTemplatePipeline;
import com.modlix.saas.adzump.model.leadzump.Stage;
import com.modlix.saas.adzump.model.leadzump.Status;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J11 - leadzump-client: the read seam to the CRM (leadzump, served by
 * entity-processor). Adzump only READS CRM runtime data - it never writes
 * tickets; the only write it transports is the product-profile enhancement
 * (a definition, J9).
 *
 * Reads carry NO @PreAuthorize (any authenticated caller) and are
 * tenant-scoped at runtime; client scope is ALWAYS taken from
 * ContextAuthentication, never from a request body.
 */
@Service
public class LeadzumpClient {

    private static final String FIXTURE_TEMPLATE_ID = "tmpl-real-estate";
    private static final String INR = "INR";

    // Wired now so the P2 real reads only swap stub bodies for feign calls; every
    // call forwards the user's own context header set (J11 §4) so
    // entity-processor enforces its own CRM authority.
    private final IFeignEntityProcessorService feignEntityProcessorService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public LeadzumpClient(IFeignEntityProcessorService feignEntityProcessorService,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {
        this.feignEntityProcessorService = feignEntityProcessorService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    public List<Product> listProducts() {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        // P0 STUB - real read lands P2 (J11): this.feignEntityProcessorService
        // .listProducts(<Authorization + clientCode + appCode + X-Forwarded-Host/Port
        // from ca>), mapping the loose rows to Product.
        // TODO(P2): tenant-scope the returned rows via isClientBeingManaged(
        // ca.getLoggedInFromClientCode(), row.clientCode); empty result throws
        // PRODUCT_NOT_FOUND only on by-id reads, list returns empty.
        return List.of(new Product()
                .setId("prd-fixture-re-1")
                .setClientCode(ca.getLoggedInFromClientCode())
                .setName("Sunrise Heights Phase 2")
                .setTemplateId(FIXTURE_TEMPLATE_ID)
                .setSiteUrl("https://sunriseheights.example.com")
                .setBrand("Sunrise Developers")
                .setAttributes(Map.of(
                        "vertical", "REAL_ESTATE",
                        "city", "Hyderabad",
                        "configuration", "2 & 3 BHK",
                        "possession", "2027-12")));
    }

    public ProductTemplatePipeline getPipeline(String productTemplateId) {

        if (StringUtil.safeIsBlank(productTemplateId))
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "productTemplateId");

        // Client context established for parity with the real read; the fixture is
        // per-template, not per-tenant.
        SecurityContextUtil.getUsersContextAuthentication();

        // P0 STUB - real read lands P2 (J11): the by-template pipeline read is an
        // EP-side addition coordinated through J11 (see the TODO on
        // IFeignEntityProcessorService); forward the user-context header set and
        // throw PRODUCT_NOT_FOUND when EP has no such template.
        return new ProductTemplatePipeline()
                .setTemplateId(productTemplateId)
                .setStages(List.of(
                        new Stage().setKey("LEAD").setName("Lead").setOrder(1),
                        new Stage().setKey("QUALIFIED").setName("Qualified").setOrder(2),
                        new Stage().setKey("SITE_VISIT").setName("Site Visit").setOrder(3),
                        new Stage().setKey("BOOKING").setName("Booking").setOrder(4)))
                .setStatuses(List.of(
                        new Status().setKey("NEW").setName("New"),
                        new Status().setKey("FOLLOW_UP").setName("Follow up"),
                        new Status().setKey("NOT_INTERESTED").setName("Not interested"),
                        new Status().setKey("JUNK").setName("Junk")));
    }

    public CrmOutcomes getOutcomes(OutcomeQuery query) {

        if (query == null || query.getIds() == null || query.getIds().isEmpty())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "ids");

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        // Client scope ALWAYS from the authentication - a clientCode in the query is
        // honored only if the logged-in client manages it.
        if (StringUtil.safeIsBlank(query.getClientCode())) {
            query.setClientCode(ca.getLoggedInFromClientCode());
        } else if (!this.isClientBeingManaged(ca.getLoggedInFromClientCode(), query.getClientCode())) {
            msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, "outcomes of client " + query.getClientCode());
        }

        Grain grain = query.getGrain() == null ? Grain.CAMPAIGN : query.getGrain();

        // P0 STUB - real read lands P2 (J11 §9): the NEW lean EP by-ID outcomes
        // endpoint (join by ad-grain id, per template, tz + date range) - see the
        // TODO on IFeignEntityProcessorService; forward the user-context header set.
        List<OutcomeRow> rows = query.getIds().stream()
                .map(id -> this.syntheticRow(id))
                .toList();

        return new CrmOutcomes().setGrain(grain).setRows(rows);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public void enhanceProduct(String productId, JsonNode profilePatch) {

        if (StringUtil.safeIsBlank(productId))
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "productId");

        if (profilePatch == null || profilePatch.isNull() || profilePatch.isEmpty())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "profilePatch");

        SecurityContextUtil.getUsersContextAuthentication();

        // P0 STUB - real write lands P2 (J11/J9): read the product via
        // this.feignEntityProcessorService.getProduct(<user-context header set>,
        // productId), enforce isClientBeingManaged(ca.getLoggedInFromClientCode(),
        // product.clientCode) (PRODUCT_NOT_FOUND / FORBIDDEN_PERMISSION), then write
        // the profile patch back through the EP patchProductProfile endpoint. This is
        // a product-DEFINITION write; CRM runtime tickets are never written.
        throw new GenericException(HttpStatus.NOT_IMPLEMENTED,
                "Product enhancement write-back lands in P2 (J11 section 5.4).");
    }

    /**
     * The files-service tenant idiom: same client, or a managed one (blocking
     * security feign).
     */
    public boolean isClientBeingManaged(String managingClientCode, String clientCode) {

        if (StringUtil.safeEquals(managingClientCode, clientCode))
            return true;

        return Boolean.TRUE.equals(this.securityService.doesClientManageClientCode(managingClientCode, clientCode));
    }

    private OutcomeRow syntheticRow(AdGrainId id) {

        // P0 STUB - real read lands P2 (J11); numbers are a plausible RE funnel so
        // downstream math (CPL/CPQL/cost-per-visit) exercises real shapes.
        return new OutcomeRow()
                .setId(id)
                .setCountByMilestone(Map.of(
                        "LEAD", 42L,
                        "QUALIFIED", 17L,
                        "SITE_VISIT", 6L,
                        "BOOKING", 2L))
                .setCostByMilestone(Map.of(
                        "LEAD", new Money(new BigDecimal("320.00"), INR),
                        "QUALIFIED", new Money(new BigDecimal("790.59"), INR),
                        "SITE_VISIT", new Money(new BigDecimal("2240.00"), INR),
                        "BOOKING", new Money(new BigDecimal("6720.00"), INR)))
                .setJunkRate(0.18d);
    }
}
