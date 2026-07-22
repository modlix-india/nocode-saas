package com.fincity.security.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dao.billing.PaymentDAO;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityInvoiceStatus;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for InvoiceService: the seller+app scoping of the filtered page read,
 * the party-to-the-invoice guard on readById, and markPaidAndEmit raising
 * INVOICE_GENERATED.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest extends AbstractServiceUnitTest {

    @Mock
    private InvoiceDAO dao;
    @Mock
    private PaymentDAO paymentDAO;
    @Mock
    private AppService appService;
    @Mock
    private ClientService clientService;
    @Mock
    private EventCreationService ecService;
    @Mock
    private SecurityMessageResourceService messageResourceService;

    private InvoiceService service;

    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong SELLER = ULong.valueOf(10);
    private static final ULong BUYER = ULong.valueOf(20);
    private static final ULong OTHER = ULong.valueOf(30);
    private static final ULong INVOICE_ID = ULong.valueOf(900);
    private static final String APP_CODE = "adzump";
    private static final String SELLER_CODE = "CCCC";

    @BeforeEach
    void setUp() {
        service = new InvoiceService(dao, paymentDAO, appService, clientService, ecService, messageResourceService);
        setupMessageResourceService(messageResourceService);
    }

    private Invoice invoice(ULong sellerId, ULong buyerId) {
        Invoice inv = new Invoice().setSellerClientId(sellerId).setClientId(buyerId).setAppId(APP_ID);
        inv.setId(INVOICE_ID);
        return inv;
    }

    // ---------------------------------------------------------------------
    // Page read scoping
    // ---------------------------------------------------------------------

    @Nested
    class PageScoping {

        private ArgumentCaptor<AbstractCondition> stubResolveAndCapture() {
            when(appService.getAppByCode(APP_CODE))
                    .thenReturn(Mono.just(TestDataFactory.createOwnApp(APP_ID, SELLER, APP_CODE)));
            when(clientService.getClientBy(SELLER_CODE))
                    .thenReturn(Mono.just(TestDataFactory.createClient(SELLER, SELLER_CODE, "BUS",
                            SecurityClientStatusCode.ACTIVE)));
            ArgumentCaptor<AbstractCondition> cap = ArgumentCaptor.forClass(AbstractCondition.class);
            when(dao.readPageFilter(any(), cap.capture())).thenReturn(Mono.just(Page.empty()));
            return cap;
        }

        @Test
        void andsSellerAppAndUserFilter() {
            ArgumentCaptor<AbstractCondition> cap = stubResolveAndCapture();
            AbstractCondition userFilter = FilterCondition.of("status", "PAID", FilterConditionOperator.EQUALS);

            StepVerifier.create(service.readPageFilter(PageRequest.of(0, 10), userFilter, APP_CODE, SELLER_CODE))
                    .expectNextCount(1)
                    .verifyComplete();

            ComplexCondition cc = (ComplexCondition) cap.getValue();
            assertEquals(ComplexConditionOperator.AND, cc.getOperator());
            assertEquals(3, cc.getConditions().size());
            assertTrue(hasFilter(cc, "sellerClientId", SELLER), "seller scope present");
            assertTrue(hasFilter(cc, "appId", APP_ID), "app scope present");
            assertTrue(hasFilter(cc, "status", "PAID"), "user filter preserved");
        }

        @Test
        void scopesSellerAndAppWhenNoUserFilter() {
            ArgumentCaptor<AbstractCondition> cap = stubResolveAndCapture();

            StepVerifier.create(service.readPageFilter(PageRequest.of(0, 10), null, APP_CODE, SELLER_CODE))
                    .expectNextCount(1)
                    .verifyComplete();

            ComplexCondition cc = (ComplexCondition) cap.getValue();
            assertEquals(ComplexConditionOperator.AND, cc.getOperator());
            assertEquals(2, cc.getConditions().size());
            assertTrue(hasFilter(cc, "sellerClientId", SELLER));
            assertTrue(hasFilter(cc, "appId", APP_ID));
        }

        private boolean hasFilter(ComplexCondition cc, String field, Object value) {
            return cc.getConditions().stream()
                    .filter(FilterCondition.class::isInstance)
                    .map(FilterCondition.class::cast)
                    .anyMatch(fc -> field.equals(fc.getField()) && value.equals(fc.getValue()));
        }
    }

    // ---------------------------------------------------------------------
    // readById party guard
    // ---------------------------------------------------------------------

    @Nested
    class ReadByIdGuard {

        @Test
        void allowsTheSeller() {
            setupSecurityContext(TestDataFactory.createBusinessAuth(SELLER, SELLER_CODE,
                    List.of("Authorities.Invoice_READ")));
            when(dao.readById(INVOICE_ID)).thenReturn(Mono.just(invoice(SELLER, BUYER)));
            when(paymentDAO.findByInvoiceIds(List.of(INVOICE_ID))).thenReturn(Flux.empty());

            StepVerifier.create(service.readById(INVOICE_ID))
                    .assertNext(inv -> assertEquals(INVOICE_ID, inv.getId()))
                    .verifyComplete();
        }

        @Test
        void allowsTheBuyer() {
            setupSecurityContext(TestDataFactory.createBusinessAuth(BUYER, "MMMM",
                    List.of("Authorities.Invoice_READ")));
            when(dao.readById(INVOICE_ID)).thenReturn(Mono.just(invoice(SELLER, BUYER)));
            when(paymentDAO.findByInvoiceIds(List.of(INVOICE_ID))).thenReturn(Flux.empty());

            StepVerifier.create(service.readById(INVOICE_ID))
                    .assertNext(inv -> assertEquals(INVOICE_ID, inv.getId()))
                    .verifyComplete();
        }

        @Test
        void forbidsAThirdParty() {
            setupSecurityContext(TestDataFactory.createBusinessAuth(OTHER, "XXXX",
                    List.of("Authorities.Invoice_READ")));
            when(dao.readById(INVOICE_ID)).thenReturn(Mono.just(invoice(SELLER, BUYER)));

            StepVerifier.create(service.readById(INVOICE_ID))
                    .verifyError(GenericException.class);
        }

        @Test
        void allowsSystemClient() {
            setupSecurityContext(TestDataFactory.createSystemAuth());
            when(dao.readById(INVOICE_ID)).thenReturn(Mono.just(invoice(SELLER, BUYER)));
            when(paymentDAO.findByInvoiceIds(List.of(INVOICE_ID))).thenReturn(Flux.empty());

            StepVerifier.create(service.readById(INVOICE_ID))
                    .assertNext(inv -> assertEquals(INVOICE_ID, inv.getId()))
                    .verifyComplete();
        }
    }

    // ---------------------------------------------------------------------
    // markPaidAndEmit
    // ---------------------------------------------------------------------

    @Test
    void markPaidAndEmitPersistsAndRaisesInvoiceGenerated() {
        Invoice invoice = new Invoice().setAppId(APP_ID).setClientId(BUYER)
                .setStatus(SecurityInvoiceStatus.PAID).setInvoiceNumber("INV/2026-27/10/1")
                .setTokensPurchased(BigDecimal.valueOf(1000)).setTotalAmount(new BigDecimal("1062.00"))
                .setCurrency("INR");
        invoice.setId(INVOICE_ID);

        when(dao.update(invoice)).thenReturn(Mono.just(invoice));
        when(appService.getAppByIdInternal(APP_ID))
                .thenReturn(Mono.just(TestDataFactory.createOwnApp(APP_ID, BUYER, APP_CODE)));
        when(clientService.getClientInfoById(BUYER))
                .thenReturn(Mono.just(TestDataFactory.createClient(BUYER, "MMMM", "BUS",
                        SecurityClientStatusCode.ACTIVE)));
        when(ecService.createEvent(any(EventQueObject.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.markPaidAndEmit(invoice))
                .assertNext(saved -> assertEquals(INVOICE_ID, saved.getId()))
                .verifyComplete();

        ArgumentCaptor<EventQueObject> cap = ArgumentCaptor.forClass(EventQueObject.class);
        verify(ecService).createEvent(cap.capture());
        EventQueObject evt = cap.getValue();
        assertEquals(EventNames.INVOICE_GENERATED, evt.getEventName());
        assertEquals(APP_CODE, evt.getAppCode());
        assertEquals("MMMM", evt.getClientCode());
        assertEquals("INV/2026-27/10/1", evt.getData().get("invoiceNumber"));
    }
}
