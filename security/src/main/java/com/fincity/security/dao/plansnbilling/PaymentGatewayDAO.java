package com.fincity.security.dao.plansnbilling;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dao.AbstractClientCheckDAO;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import static com.fincity.security.jooq.tables.SecurityPaymentGateway.SECURITY_PAYMENT_GATEWAY;
import com.fincity.security.jooq.tables.records.SecurityPaymentGatewayRecord;

import reactor.core.publisher.Mono;

@Component
public class PaymentGatewayDAO extends AbstractClientCheckDAO<SecurityPaymentGatewayRecord, ULong, PaymentGateway> {

    public PaymentGatewayDAO() {
        super(PaymentGateway.class, SECURITY_PAYMENT_GATEWAY, SECURITY_PAYMENT_GATEWAY.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_PAYMENT_GATEWAY.CLIENT_ID;
    }

    public Mono<PaymentGateway> findByClientIdAndGateway(ULong clientId, SecurityPaymentGatewayPaymentGateway gateway) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_PAYMENT_GATEWAY)
                .where(SECURITY_PAYMENT_GATEWAY.CLIENT_ID.eq(clientId)
                        .and(SECURITY_PAYMENT_GATEWAY.PAYMENT_GATEWAY.eq(gateway))))
                .map(record -> record.into(PaymentGateway.class));
    }
}
