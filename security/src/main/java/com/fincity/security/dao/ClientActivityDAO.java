package com.fincity.security.dao;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.ClientActivity;
import com.fincity.security.jooq.tables.SecurityClientActivity;
import com.fincity.security.jooq.tables.records.SecurityClientActivityRecord;

@Component
public class ClientActivityDAO extends AbstractDAO<SecurityClientActivityRecord, ULong, ClientActivity> {

    protected ClientActivityDAO() {
        super(ClientActivity.class, SecurityClientActivity.SECURITY_CLIENT_ACTIVITY,
                SecurityClientActivity.SECURITY_CLIENT_ACTIVITY.ID);
    }
}
