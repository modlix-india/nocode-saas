package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dto.App;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

@Service
public class AppDAO extends AbstractClientCheckDAO<SecurityAppRecord, ULong, App>{

	protected AppDAO() {
		super(App.class, SECURITY_APP, SECURITY_APP.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_APP.CLIENT_ID;
	}
}
