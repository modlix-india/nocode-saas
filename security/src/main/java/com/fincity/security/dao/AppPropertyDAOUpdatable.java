package com.fincity.security.dao;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.clientcheck.AbstractUpdatableClientCheckDAO;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.jooq.tables.SecurityAppProperty;
import com.fincity.security.jooq.tables.records.SecurityAppPropertyRecord;

@Service
public class AppPropertyDAOUpdatable extends AbstractUpdatableClientCheckDAO<SecurityAppPropertyRecord, ULong, AppProperty> {

    protected AppPropertyDAOUpdatable() {
        super(AppProperty.class, SecurityAppProperty.SECURITY_APP_PROPERTY,
                SecurityAppProperty.SECURITY_APP_PROPERTY.ID);
    }

    @Override
    public Field<ULong> getClientIDField() {
        return SecurityAppProperty.SECURITY_APP_PROPERTY.CLIENT_ID;
    }

}
