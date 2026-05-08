package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorDiagnostics.ENTITY_PROCESSOR_DIAGNOSTICS;

import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowDAO;
import com.fincity.saas.entity.processor.dto.DiagnosticsLog;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorDiagnosticsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticsDAO extends AbstractFlowDAO<EntityProcessorDiagnosticsRecord, ULong, DiagnosticsLog> {

    protected DiagnosticsDAO() {
        super(DiagnosticsLog.class, ENTITY_PROCESSOR_DIAGNOSTICS, ENTITY_PROCESSOR_DIAGNOSTICS.ID);
    }
}
