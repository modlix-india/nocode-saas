package com.fincity.security.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.billing.AppBillingBundle;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleBundleType;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityInvoiceStatus;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for token-bundle purchase: FIXED tier pricing, CUSTOM price-per-token
 * with min/max validation, GST-on-top, and the snapshotted PENDING invoice.
 */
@ExtendWith(MockitoExtension.class)
class BundlePurchaseServiceTest extends AbstractServiceUnitTest {

    @Mock
    private AppBillingBundleService bundleService;
    @Mock
    private AppBillingConfigService configService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private InvoiceDAO invoiceDAO;
    @Mock
    private RazorpayPaymentService razorpayService;
    @Mock
    private ClientService clientService;
    @Mock
    private SecurityMessageResourceService messageResourceService;

    private BundlePurchaseService service;

    private static final ULong BUNDLE_ID = ULong.valueOf(70);
    private static final ULong CONFIG_ID = ULong.valueOf(50);
    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong C_CLIENT = ULong.valueOf(10);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final ULong INVOICE_ID = ULong.valueOf(900);

    @BeforeEach
    void setUp() {
        service = new BundlePurchaseService(bundleService, configService, invoiceService, invoiceDAO,
                razorpayService, clientService, messageResourceService);
        setupMessageResourceService(messageResourceService);
        setupSecurityContext(TestDataFactory.createBusinessAuth(M_CLIENT, "MMMM",
                List.of("Authorities.ROLE_Owner")));

        Client buyer = TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS", SecurityClientStatusCode.ACTIVE)
                .setName("Acme Pvt Ltd");
        lenient().when(clientService.getClientInfoById(M_CLIENT)).thenReturn(Mono.just(buyer));
        lenient().when(invoiceDAO.nextInvoiceNumber(eq(C_CLIENT), anyString()))
                .thenReturn(Mono.just("INV/2026-27/10/1"));
        lenient().when(invoiceService.create(any())).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(INVOICE_ID);
            return Mono.just(i);
        });
        lenient().when(razorpayService.initialize(any(), any())).thenReturn(Mono.just("https://rzp.io/i/short"));
    }

    private AppBillingConfig config(BigDecimal gstPct) {
        AppBillingConfig c = new AppBillingConfig().setAppId(APP_ID).setClientId(C_CLIENT)
                .setGstPercentage(gstPct).setSellerLegalName("Modlix").setSellerGstin("29ABCDE1234F1Z5");
        c.setId(CONFIG_ID);
        return c;
    }

    private AppBillingBundle bundle(SecurityAppBillingBundleBundleType type) {
        AppBillingBundle b = new AppBillingBundle().setBillingConfigId(CONFIG_ID).setBundleType(type)
                .setLabel("Starter").setCurrency("INR");
        b.setId(BUNDLE_ID);
        return b;
    }

    @Test
    void fixedBundleAddsGstOnTopAndSnapshotsInvoice() {
        AppBillingBundle b = bundle(SecurityAppBillingBundleBundleType.FIXED)
                .setTokens(BigDecimal.valueOf(1000)).setPrice(BigDecimal.valueOf(900));
        when(bundleService.readForPurchase(BUNDLE_ID)).thenReturn(Mono.just(b));
        when(configService.readInternal(CONFIG_ID)).thenReturn(Mono.just(config(BigDecimal.valueOf(18))));

        StepVerifier.create(service.purchase(BUNDLE_ID, null, null))
                .assertNext(r -> {
                    assertEquals(INVOICE_ID, r.invoiceId());
                    assertEquals("https://rzp.io/i/short", r.paymentUrl());
                    assertEquals(new BigDecimal("1062.00"), r.totalAmount());
                })
                .verifyComplete();

        ArgumentCaptor<Invoice> cap = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceService).create(cap.capture());
        Invoice inv = cap.getValue();
        assertEquals(SecurityInvoiceStatus.PENDING, inv.getStatus());
        assertEquals(C_CLIENT, inv.getSellerClientId());
        assertEquals(M_CLIENT, inv.getClientId());
        assertEquals("Acme Pvt Ltd", inv.getBuyerLegalName());
        assertEquals(BigDecimal.valueOf(1000), inv.getTokensPurchased());
        assertEquals(BigDecimal.valueOf(900), inv.getBaseAmount());
        assertEquals(new BigDecimal("162.00"), inv.getGstAmount());
        assertEquals(new BigDecimal("1062.00"), inv.getTotalAmount());
        assertEquals("INR", inv.getCurrency());
    }

    @Test
    void customBundlePricesAtPricePerToken() {
        AppBillingBundle b = bundle(SecurityAppBillingBundleBundleType.CUSTOM)
                .setPricePerToken(new BigDecimal("0.9"))
                .setMinTokens(BigDecimal.valueOf(500))
                .setMaxTokens(BigDecimal.valueOf(5000));
        when(bundleService.readForPurchase(BUNDLE_ID)).thenReturn(Mono.just(b));
        when(configService.readInternal(CONFIG_ID)).thenReturn(Mono.just(config(BigDecimal.valueOf(18))));

        StepVerifier.create(service.purchase(BUNDLE_ID, BigDecimal.valueOf(1000), null))
                .assertNext(r -> assertEquals(new BigDecimal("1062.00"), r.totalAmount()))
                .verifyComplete();

        ArgumentCaptor<Invoice> cap = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceService).create(cap.capture());
        assertEquals(BigDecimal.valueOf(1000), cap.getValue().getTokensPurchased());
        assertEquals(new BigDecimal("900.0"), cap.getValue().getBaseAmount());
    }

    @Test
    void customBundleRejectsTokensBelowMinimum() {
        AppBillingBundle b = bundle(SecurityAppBillingBundleBundleType.CUSTOM)
                .setPricePerToken(new BigDecimal("0.9"))
                .setMinTokens(BigDecimal.valueOf(500));
        when(bundleService.readForPurchase(BUNDLE_ID)).thenReturn(Mono.just(b));
        when(configService.readInternal(CONFIG_ID)).thenReturn(Mono.just(config(BigDecimal.valueOf(18))));

        StepVerifier.create(service.purchase(BUNDLE_ID, BigDecimal.valueOf(100), null))
                .expectError()
                .verify();

        verify(invoiceService, never()).create(any());
    }

    @Test
    void customBundleRequiresTokenQuantity() {
        AppBillingBundle b = bundle(SecurityAppBillingBundleBundleType.CUSTOM)
                .setPricePerToken(new BigDecimal("0.9"));
        when(bundleService.readForPurchase(BUNDLE_ID)).thenReturn(Mono.just(b));
        when(configService.readInternal(CONFIG_ID)).thenReturn(Mono.just(config(BigDecimal.valueOf(18))));

        StepVerifier.create(service.purchase(BUNDLE_ID, null, null))
                .expectError()
                .verify();

        verify(invoiceService, never()).create(any());
    }
}
