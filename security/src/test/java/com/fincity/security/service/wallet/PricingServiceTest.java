package com.fincity.security.service.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.dto.billing.AppActionCost;
import com.fincity.security.jooq.enums.SecurityAppActionCostActionClass;
import com.fincity.security.model.billing.ActionClass;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link PricingService}. Costs are owned by the per-(app,client)
 * billing config; there is no global catalog. These cases pin the pricing rules
 * the six billing scenarios rely on: a config with no cost row (or no config at
 * all) is free, a present row is unit-cost x quantity, and the action class
 * (ENGAGEMENT grace vs METERED block) flows through.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingServiceTest {

    @Mock
    private AppActionCostDAO appActionCostDAO;

    private PricingService service;

    private static final ULong CONFIG_ID = ULong.valueOf(7);
    private static final String ACTION = "core.email.send";

    @BeforeEach
    void setUp() {
        service = new PricingService(appActionCostDAO);
    }

    private AppActionCost cost(BigDecimal unit, SecurityAppActionCostActionClass cls) {
        return new AppActionCost().setBillingConfigId(CONFIG_ID).setActionKey(ACTION)
                .setCreditCost(unit).setActionClass(cls);
    }

    @Test
    void resolveCost_nullConfigId_isFreeAndMetered() {
        StepVerifier.create(service.resolveCost(null, ACTION, BigDecimal.TEN))
                .assertNext(rc -> {
                    assertEquals(0, rc.credits().signum());
                    assertEquals(ActionClass.METERED, rc.actionClass());
                })
                .verifyComplete();
    }

    @Test
    void resolveCost_noCostRowUnderConfig_isFreeAndMetered() {
        when(appActionCostDAO.findByConfigAndActionKey(eq(CONFIG_ID), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.resolveCost(CONFIG_ID, ACTION, BigDecimal.valueOf(5)))
                .assertNext(rc -> {
                    assertEquals(0, rc.credits().signum());
                    assertEquals(ActionClass.METERED, rc.actionClass());
                })
                .verifyComplete();
    }

    @Test
    void resolveCost_meteredRow_isUnitCostTimesQuantity() {
        when(appActionCostDAO.findByConfigAndActionKey(CONFIG_ID, ACTION))
                .thenReturn(Mono.just(cost(new BigDecimal("2.5"), SecurityAppActionCostActionClass.METERED)));

        StepVerifier.create(service.resolveCost(CONFIG_ID, ACTION, BigDecimal.valueOf(4)))
                .assertNext(rc -> {
                    assertEquals(0, new BigDecimal("10.0").compareTo(rc.credits()));
                    assertEquals(ActionClass.METERED, rc.actionClass());
                })
                .verifyComplete();
    }

    @Test
    void resolveCost_engagementRow_classFlowsThrough() {
        when(appActionCostDAO.findByConfigAndActionKey(CONFIG_ID, ACTION))
                .thenReturn(Mono.just(cost(BigDecimal.ONE, SecurityAppActionCostActionClass.ENGAGEMENT)));

        StepVerifier.create(service.resolveCost(CONFIG_ID, ACTION, BigDecimal.ONE))
                .assertNext(rc -> assertEquals(ActionClass.ENGAGEMENT, rc.actionClass()))
                .verifyComplete();
    }

    @Test
    void resolveCost_nullQuantity_defaultsToOne() {
        when(appActionCostDAO.findByConfigAndActionKey(CONFIG_ID, ACTION))
                .thenReturn(Mono.just(cost(new BigDecimal("3"), SecurityAppActionCostActionClass.METERED)));

        StepVerifier.create(service.resolveCost(CONFIG_ID, ACTION, null))
                .assertNext(rc -> assertEquals(0, new BigDecimal("3").compareTo(rc.credits())))
                .verifyComplete();
    }

    @Test
    void resolveCost_nullCreditCost_isZero() {
        when(appActionCostDAO.findByConfigAndActionKey(CONFIG_ID, ACTION))
                .thenReturn(Mono.just(cost(null, SecurityAppActionCostActionClass.METERED)));

        StepVerifier.create(service.resolveCost(CONFIG_ID, ACTION, BigDecimal.TEN))
                .assertNext(rc -> assertEquals(0, rc.credits().signum()))
                .verifyComplete();
    }

    @Test
    void resolveCost_nullActionClass_defaultsToMetered() {
        when(appActionCostDAO.findByConfigAndActionKey(CONFIG_ID, ACTION))
                .thenReturn(Mono.just(cost(BigDecimal.ONE, null)));

        StepVerifier.create(service.resolveCost(CONFIG_ID, ACTION, BigDecimal.ONE))
                .assertNext(rc -> assertEquals(ActionClass.METERED, rc.actionClass()))
                .verifyComplete();
    }
}
