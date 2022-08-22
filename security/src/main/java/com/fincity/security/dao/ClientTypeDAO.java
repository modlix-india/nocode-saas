package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientType.SECURITY_CLIENT_TYPE;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.ClientType;
import com.fincity.security.jooq.tables.records.SecurityClientTypeRecord;

@Component
public class ClientTypeDAO extends AbstractUpdatableDAO<SecurityClientTypeRecord, ULong, ClientType>{

	protected ClientTypeDAO() {
		super(ClientType.class, SECURITY_CLIENT_TYPE, SECURITY_CLIENT_TYPE.ID);
	}
	
	

}
