package com.fincity.security.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.UserRequest;
import com.fincity.security.jooq.enums.SecurityUserRequestStatus;
import com.fincity.security.jooq.tables.records.SecurityUserRequestRecord;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


import static com.fincity.security.jooq.Tables.SECURITY_USER_REQUEST;

@Component
public class UserRequestDAO extends AbstractUpdatableDAO<SecurityUserRequestRecord, ULong, UserRequest> {

    public UserRequestDAO() {
        super(UserRequest.class, SECURITY_USER_REQUEST, SECURITY_USER_REQUEST.ID);
    }

    public Mono<Boolean> checkPendingRequestExists(ULong userId, ULong appId) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(SECURITY_USER_REQUEST)
                        .where(DSL.and(
                                SECURITY_USER_REQUEST.USER_ID.eq(userId),
                                SECURITY_USER_REQUEST.APP_ID.eq(appId),
                                SECURITY_USER_REQUEST.STATUS.eq(SecurityUserRequestStatus.PENDING)
                        )))
                .map(Record1::value1)
                .map(count -> count > 0);
    }

    public Mono<UserRequest> readByRequestId(String requestId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_USER_REQUEST)
                .where(SECURITY_USER_REQUEST.REQUEST_ID.eq(requestId)))
                .map(e -> e.into(this.pojoClass));
    }


}
