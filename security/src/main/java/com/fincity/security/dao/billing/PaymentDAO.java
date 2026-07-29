package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_PAYMENT;

import java.util.Collection;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.Payment;
import com.fincity.security.jooq.tables.records.SecurityPaymentRecord;

import reactor.core.publisher.Flux;
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

    /** Every payment for the given invoices (for enriching invoice reads with the method used). */
    public Flux<Payment> findByInvoiceIds(Collection<ULong> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty())
            return Flux.empty();
        return Flux.from(this.dslContext.selectFrom(SECURITY_PAYMENT)
                .where(SECURITY_PAYMENT.INVOICE_ID.in(invoiceIds)))
                .map(r -> r.into(Payment.class));
    }
}
