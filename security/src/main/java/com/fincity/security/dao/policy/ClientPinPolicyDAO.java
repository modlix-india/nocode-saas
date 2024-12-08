package com.fincity.security.dao.policy;

import static com.fincity.security.jooq.tables.SecurityClientPinPolicy.SECURITY_CLIENT_PIN_POLICY;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPinPolicyRecord;

@Component
public class ClientPinPolicyDAO extends AbstractPolicyDao<SecurityClientPinPolicyRecord, ClientPinPolicy> {

    public ClientPinPolicyDAO() {
        super(ClientPinPolicy.class, SECURITY_CLIENT_PIN_POLICY, SECURITY_CLIENT_PIN_POLICY.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_CLIENT_PIN_POLICY.CLIENT_ID;
    }

    @Override
    protected Field<ULong> getAppIdField() {
        return SECURITY_CLIENT_PIN_POLICY.APP_ID;
    }
}
