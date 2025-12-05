package com.fincity.security.controller;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.plansnbilling.PaymentDAO;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;
import com.fincity.security.jooq.tables.records.SecurityPaymentRecord;
import com.fincity.security.service.plansnbilling.PaymentService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/payments")
public class PaymentController
        extends AbstractJOOQUpdatableDataController<SecurityPaymentRecord, ULong, Payment, PaymentDAO, PaymentService> {

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/initialize")
    public Mono<ResponseEntity<Payment>> initializePayment(
            @RequestParam ULong invoiceId,
            @RequestParam SecurityPaymentGatewayPaymentGateway gateway,
            @RequestBody(required = false) Map<String, Object> metadata) {
        return this.service.initializePayment(invoiceId, gateway, metadata)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/callback/{clientId}/{gateway}")
    public Mono<ResponseEntity<Payment>> processCallback(
            @PathVariable ULong clientId,
            @PathVariable SecurityPaymentGatewayPaymentGateway gateway,
            @RequestBody Map<String, Object> callbackData) {
        return this.service.processCallback(clientId, gateway, callbackData)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/invoice/{invoiceId}")
    public Mono<ResponseEntity<List<Payment>>> getPaymentsByInvoiceId(@PathVariable ULong invoiceId) {
        return this.service.getPaymentsByInvoiceId(invoiceId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{paymentId}/status")
    public Mono<ResponseEntity<Payment>> updatePaymentStatus(
            @PathVariable ULong paymentId,
            @RequestParam SecurityPaymentPaymentStatus status) {
        return this.service.updatePaymentStatus(paymentId, status)
                .map(ResponseEntity::ok);
    }
}
