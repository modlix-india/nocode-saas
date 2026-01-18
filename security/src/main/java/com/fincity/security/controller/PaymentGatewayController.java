package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.plansnbilling.PaymentGatewayDAO;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.tables.records.SecurityPaymentGatewayRecord;
import com.fincity.security.service.plansnbilling.PaymentGatewayService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/payment-gateways")
public class PaymentGatewayController extends AbstractJOOQUpdatableDataController<SecurityPaymentGatewayRecord, ULong,
        PaymentGateway, PaymentGatewayDAO, PaymentGatewayService> {

    public PaymentGatewayController(PaymentGatewayService service) {
        this.service = service;
    }

    @GetMapping("/client/{clientId}/gateway/{gateway}")
    public Mono<ResponseEntity<PaymentGateway>> getPaymentGatewayByClientAndGateway(
            @PathVariable ULong clientId,
            @PathVariable SecurityPaymentGatewayPaymentGateway gateway) {
        return this.service.findByClientIdAndGateway(clientId, gateway)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}
