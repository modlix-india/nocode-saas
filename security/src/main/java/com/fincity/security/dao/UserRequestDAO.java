package com.fincity.security.dao;

import static com.fincity.security.jooq.Tables.SECURITY_USER_REQUEST;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.clientcheck.AbstractUpdatableClientCheckDAO;
import com.fincity.security.dao.clientcheck.ClientCheckDAOHelper;
import com.fincity.security.dto.UserRequest;
import com.fincity.security.jooq.enums.SecurityUserRequestStatus;
import com.fincity.security.jooq.tables.records.SecurityUserRequestRecord;

import reactor.core.publisher.Mono;

@Component
public class UserRequestDAO extends AbstractUpdatableClientCheckDAO<SecurityUserRequestRecord, ULong, UserRequest> {

    public UserRequestDAO() {
        super(UserRequest.class, SECURITY_USER_REQUEST, SECURITY_USER_REQUEST.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_USER_REQUEST.CLIENT_ID;
    }

    /**
     * Every filtered read of this table is restricted to the requests the
     * signed-in user is entitled to act on - the caller's own client, or a client
     * below it in the hierarchy that the caller manages. Same rule, same code, as
     * the invite listing.
     */
    @Override
    public Mono<Condition> filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {

        return ClientCheckDAOHelper.applyOwnAndManagedClientFilter(
                this.baseFilter(condition, selectJoinStep), SECURITY_USER_REQUEST.CLIENT_ID);
    }

    public Mono<Boolean> checkPendingRequestExists(ULong userId, ULong appId) {
        return Mono.from(this.dslContext
                .selectCount()
                .from(SECURITY_USER_REQUEST)
                .where(DSL.and(
                        SECURITY_USER_REQUEST.USER_ID.eq(userId),
                        SECURITY_USER_REQUEST.APP_ID.eq(appId),
                        SECURITY_USER_REQUEST.STATUS.eq(SecurityUserRequestStatus.PENDING))))
                .map(Record1::value1)
                .map(count -> count > 0);
    }

    /**
     * Unscoped lookup by the external request id. Every caller must pass the
     * result through the entitlement check in {@code UserRequestService} before
     * acting on it - the request id alone is not an authorisation.
     */
    public Mono<UserRequest> readByRequestId(String requestId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_USER_REQUEST)
                .where(SECURITY_USER_REQUEST.REQUEST_ID.eq(requestId)))
                .map(e -> e.into(this.pojoClass));
    }
}
