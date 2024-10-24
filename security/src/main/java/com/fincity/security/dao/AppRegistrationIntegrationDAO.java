package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dto.AppRegistrationIntegration;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.jooq.tables.SecurityAppRegIntegration;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationRecord;

import reactor.core.publisher.Mono;

@Service
public class AppRegistrationIntegrationDAO
                extends AbstractClientCheckDAO<SecurityAppRegIntegrationRecord, ULong, AppRegistrationIntegration> {

        protected AppRegistrationIntegrationDAO() {
                super(AppRegistrationIntegration.class, SECURITY_APP_REG_INTEGRATION,
                                SECURITY_APP_REG_INTEGRATION.ID);
        }

        @Override
        protected Field<ULong> getClientIDField() {
                return SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION.CLIENT_ID;
        }

        public Mono<ULong> getIntegrationId(ULong appId, ULong clientId) {

                return Mono.from(
                                this.dslContext.select(SECURITY_APP_REG_INTEGRATION.ID)
                                                .from(SECURITY_APP_REG_INTEGRATION)
                                                .where(SECURITY_APP_REG_INTEGRATION.APP_ID.eq(appId)
                                                                .and(SECURITY_APP_REG_INTEGRATION.CLIENT_ID
                                                                                .eq(clientId)))
                                                .limit(1))
                                .map(Record1::value1);
        }

        public Mono<AppRegistrationIntegration> getIntegration(ULong appId, ULong clientId,
                        SecurityAppRegIntegrationPlatform platform) {

                return Mono
                                .from(this.dslContext.select(SECURITY_APP_REG_INTEGRATION.fields())
                                                .from(SECURITY_APP_REG_INTEGRATION)
                                                .where(SECURITY_APP_REG_INTEGRATION.APP_ID.eq(appId)
                                                                .and(SECURITY_APP_REG_INTEGRATION.CLIENT_ID
                                                                                .eq(clientId))
                                                                .and(SECURITY_APP_REG_INTEGRATION.PLATFORM
                                                                                .eq(platform)))
                                                .limit(1))
                                .map(e -> e.into(AppRegistrationIntegration.class));
        }
}