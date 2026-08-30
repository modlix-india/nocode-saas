package com.fincity.security.dao;

import static com.fincity.security.jooq.Tables.SECURITY_USER_REQUEST;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;

import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

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
     * Ids of users whose name, user name or email matches the given text.
     * <p>
     * This exists so the requests listing can be searched by person. The
     * requester's name lives on {@code SECURITY_USER}, not on this table, so a
     * caller-supplied condition cannot express it; the service turns the match
     * into a {@code userId IN (...)} condition instead.
     * <p>
     * No client scoping is applied and none is needed: nothing about the users is
     * returned, and the request rows that come back are still filtered by
     * {@link #filter}. Capped so a one-character search cannot pull the whole user
     * table into a WHERE clause.
     */
    public Mono<List<ULong>> userIdsMatching(String text) {

        String like = "%" + text + "%";

        // The full name is matched as well as the parts. Searching per column only
        // means typing someone's whole name finds nothing, which is the first thing
        // anyone tries. COALESCE because CONCAT returns null if either half is.
        Field<String> fullName = DSL.concat(
                DSL.coalesce(SECURITY_USER.FIRST_NAME, DSL.val("")),
                DSL.val(" "),
                DSL.coalesce(SECURITY_USER.LAST_NAME, DSL.val("")));

        return Flux.from(this.dslContext.select(SECURITY_USER.ID)
                .from(SECURITY_USER)
                .where(DSL.or(
                        SECURITY_USER.FIRST_NAME.like(like),
                        SECURITY_USER.LAST_NAME.like(like),
                        SECURITY_USER.USER_NAME.like(like),
                        SECURITY_USER.EMAIL_ID.like(like),
                        fullName.like(like)))
                .limit(500))
                .map(r -> r.get(SECURITY_USER.ID))
                .collectList();
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
