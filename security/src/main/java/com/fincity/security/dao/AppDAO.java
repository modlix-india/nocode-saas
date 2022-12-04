package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.App;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

@Service
public class AppDAO extends AbstractUpdatableDAO<SecurityAppRecord, ULong, App>{

	protected AppDAO() {
		super(App.class, SECURITY_APP, SECURITY_APP.ID);
	}

}
