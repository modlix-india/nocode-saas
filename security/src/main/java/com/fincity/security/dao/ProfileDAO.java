package com.fincity.security.dao;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Profile;
import com.fincity.security.jooq.tables.SecurityProfile;
import com.fincity.security.jooq.tables.records.SecurityProfileRecord;

@Component
public class ProfileDAO extends AbstractClientCheckDAO<SecurityProfileRecord, ULong, Profile> {

    public ProfileDAO() {
        super(Profile.class, SecurityProfile.SECURITY_PROFILE, SecurityProfile.SECURITY_PROFILE.CLIENT_ID);
    }

    @Override
    public Field<ULong> getClientIDField() {
        return SecurityProfile.SECURITY_PROFILE.CLIENT_ID;
    }
}