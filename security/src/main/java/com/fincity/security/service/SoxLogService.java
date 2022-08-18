package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.SoxLogDAO;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.jooq.tables.records.SecuritySoxLogRecord;
import com.fincity.security.jwt.ContextUser;
import com.fincity.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

@Service
public class SoxLogService extends AbstractDataService<SecuritySoxLogRecord, ULong, SoxLog, SoxLogDAO> {

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser()
		        .map(ContextUser::getId)
		        .map(ULong::valueOf);
	}
}
