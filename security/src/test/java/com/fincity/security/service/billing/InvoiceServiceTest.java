package com.fincity.security.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityInvoiceStatus;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for InvoiceService: markPaidAndEmit persists the PAID invoice and
 * raises INVOICE_GENERATED (which the seeded EventActions turn into PDF + email).
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceDAO dao;
    @Mock
    private AppService appService;
    @Mock
    private ClientService clientService;
    @Mock
    private EventCreationService ecService;

    private InvoiceService service;

    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final ULong INVOICE_ID = ULong.valueOf(900);

    @BeforeEach
    void setUp() {
        service = new InvoiceService(dao, appService, clientService, ecService);
    }

    @Test
    void markPaidAndEmitPersistsAndRaisesInvoiceGenerated() {
        Invoice invoice = new Invoice().setAppId(APP_ID).setClientId(M_CLIENT)
                .setStatus(SecurityInvoiceStatus.PAID).setInvoiceNumber("INV/2026-27/10/1")
                .setTokensPurchased(BigDecimal.valueOf(1000)).setTotalAmount(new BigDecimal("1062.00"))
                .setCurrency("INR");
        invoice.setId(INVOICE_ID);

        when(dao.update(invoice)).thenReturn(Mono.just(invoice));
        when(appService.getAppByIdInternal(APP_ID))
                .thenReturn(Mono.just(TestDataFactory.createOwnApp(APP_ID, M_CLIENT, "adzump")));
        when(clientService.getClientInfoById(M_CLIENT))
                .thenReturn(Mono.just(TestDataFactory.createClient(M_CLIENT, "MMMM", "BUS",
                        SecurityClientStatusCode.ACTIVE)));
        when(ecService.createEvent(any(EventQueObject.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.markPaidAndEmit(invoice))
                .assertNext(saved -> assertEquals(INVOICE_ID, saved.getId()))
                .verifyComplete();

        ArgumentCaptor<EventQueObject> cap = ArgumentCaptor.forClass(EventQueObject.class);
        verify(ecService).createEvent(cap.capture());
        EventQueObject evt = cap.getValue();
        assertEquals(EventNames.INVOICE_GENERATED, evt.getEventName());
        assertEquals("adzump", evt.getAppCode());
        assertEquals("MMMM", evt.getClientCode());
        assertEquals("INV/2026-27/10/1", evt.getData().get("invoiceNumber"));
    }
}
