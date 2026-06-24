package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_PAYMENT;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.Payment;
import com.fincity.security.jooq.tables.records.SecurityPaymentRecord;

import reactor.core.publisher.Mono;

@Component
public class PaymentDAO extends AbstractUpdatableDAO<SecurityPaymentRecord, ULong, Payment> {

    protected PaymentDAO() {
        super(Payment.class, SECURITY_PAYMENT, SECURITY_PAYMENT.ID);
    }

    public Mono<Payment> findByGatewayOrderId(String gatewayOrderId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_PAYMENT)
                .where(SECURITY_PAYMENT.GATEWAY_ORDER_ID.eq(gatewayOrderId)))
                .map(r -> r.into(Payment.class));
    }
}
