package com.fincity.security.service.plansnbilling;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.plansnbilling.InvoiceDAO;
import com.fincity.security.dao.plansnbilling.PlanCycleDAO;
import com.fincity.security.dao.plansnbilling.PlanDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;
import com.fincity.security.service.AbstractSecurityUpdatableDataService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class InvoiceService
        extends AbstractSecurityUpdatableDataService<SecurityInvoiceRecord, ULong, Invoice, InvoiceDAO> {

    private final InvoiceDAO invoiceDAO;
    private final PlanDAO planDAO;
    private final PlanCycleDAO planCycleDAO;

    public InvoiceService(InvoiceDAO invoiceDAO, PlanCycleDAO planCycleDAO, PlanDAO planDAO) {
        this.invoiceDAO = invoiceDAO;
        this.planCycleDAO = planCycleDAO;
        this.planDAO = planDAO;
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.INVOICE;
    }

    public Mono<Void> createInvoices() {

        Flux.from(planDAO.getClientPlansForInvoiceGeneration())
        .flatMap(clientPlan -> {
    }
}
