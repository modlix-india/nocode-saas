package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.SoxLogDAO;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecuritySoxLogRecord;

import reactor.core.publisher.Mono;

@Service
public class SoxLogService extends AbstractJOOQDataService<SecuritySoxLogRecord, ULong, SoxLog, SoxLogDAO> {

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser()
				.map(ContextUser::getId)
				.map(ULong::valueOf);
	}

	public void createLog(ULong objectId, SecuritySoxLogActionName actionName, SecuritySoxLogObjectName objectName,
			String description) {

		this.create(new SoxLog().setObjectId(objectId)
				.setActionName(actionName)
				.setObjectName(objectName)
				.setDescription(description))
				.subscribe();
	}
}
