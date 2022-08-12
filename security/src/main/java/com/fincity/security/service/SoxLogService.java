package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.SoxLogDAO;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.jooq.tables.records.SecuritySoxLogRecord;

@Service
public class SoxLogService extends AbstractDataService<SecuritySoxLogRecord, ULong, SoxLog, SoxLogDAO> {

}
