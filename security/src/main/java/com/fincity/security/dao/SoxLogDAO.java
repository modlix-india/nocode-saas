package com.fincity.security.dao;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.SoxLog;
import com.fincity.security.jooq.tables.SecuritySoxLog;
import com.fincity.security.jooq.tables.records.SecuritySoxLogRecord;

@Component
public class SoxLogDAO extends AbstractDAO<SecuritySoxLogRecord, ULong, SoxLog> {

	protected SoxLogDAO() {
		super(SoxLog.class, SecuritySoxLog.SECURITY_SOX_LOG, SecuritySoxLog.SECURITY_SOX_LOG.ID);
	}

}
