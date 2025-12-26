package com.fincity.security.dao.plansnbilling;

import static com.fincity.security.jooq.tables.SecurityPayment.SECURITY_PAYMENT;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;
import com.fincity.security.jooq.tables.records.SecurityPaymentRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class PaymentDAO extends AbstractUpdatableDAO<SecurityPaymentRecord, ULong, Payment> {

    public PaymentDAO() {
        super(Payment.class, SECURITY_PAYMENT, SECURITY_PAYMENT.ID);
    }

    public Mono<List<Payment>> findByInvoiceId(ULong invoiceId) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_PAYMENT)
                .where(SECURITY_PAYMENT.INVOICE_ID.eq(invoiceId)))
                .map(record -> record.into(Payment.class))
                .collectList();
    }

    public Mono<Payment> findByInvoiceIdAndReference(ULong invoiceId, String paymentReference) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_PAYMENT)
                .where(SECURITY_PAYMENT.INVOICE_ID.eq(invoiceId)
                        .and(SECURITY_PAYMENT.PAYMENT_REFERENCE.eq(paymentReference))))
                .map(record -> record.into(Payment.class));
    }

    public Mono<List<Payment>> findByInvoiceIdAndStatus(ULong invoiceId, SecurityPaymentPaymentStatus status) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_PAYMENT)
                .where(SECURITY_PAYMENT.INVOICE_ID.eq(invoiceId)
                        .and(SECURITY_PAYMENT.PAYMENT_STATUS.eq(status))))
                .map(record -> record.into(Payment.class))
                .collectList();
    }

    public Mono<Payment> findByPaymentReference(String paymentReference) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_PAYMENT)
                .where(SECURITY_PAYMENT.PAYMENT_REFERENCE.eq(paymentReference)))
                .map(record -> record.into(Payment.class));
    }
}
