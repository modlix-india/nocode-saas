package com.fincity.security.dao;

import static com.fincity.security.jooq.Tables.SECURITY_USER_INVITE;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.security.dao.clientcheck.AbstractClientCheckDAO;
import com.fincity.security.dao.clientcheck.ClientCheckDAOHelper;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.jooq.tables.records.SecurityUserInviteRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UserInviteDAO extends AbstractClientCheckDAO<SecurityUserInviteRecord, ULong, UserInvite> {

    public UserInviteDAO() {
        super(UserInvite.class, SECURITY_USER_INVITE, SECURITY_USER_INVITE.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_USER_INVITE.CLIENT_ID;
    }

    /**
     * An invite row carries a live {@code INVITE_CODE}, which is all anyone needs
     * to accept the invite on the permitted {@code /acceptInvite} route. So every
     * filtered read is restricted to the caller's own client and the clients they
     * manage, the same rule the access-request listing uses.
     */
    @Override
    public Mono<Condition> filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {

        return ClientCheckDAOHelper.applyOwnAndManagedClientFilter(
                this.baseFilter(condition, selectJoinStep), SECURITY_USER_INVITE.CLIENT_ID);
    }

    /**
     * Nested conditions must not each re-apply the client scoping - {@link #filter}
     * already ANDs it in once, at the top.
     */
    @Override
    protected Mono<Condition> complexConditionFilter(ComplexCondition cc, SelectJoinStep<Record> selectJoinStep) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty())
            return Mono.just(DSL.noCondition());

        return Flux.concat(cc.getConditions().stream()
                .map(condition -> this.baseFilter(condition, selectJoinStep))
                .toList())
                .collectList()
                .map(conditions -> cc.getOperator() == ComplexConditionOperator.AND
                        ? DSL.and(conditions)
                        : DSL.or(conditions));
    }

    public Mono<UserInvite> getUserInvitation(String code) {

        return Mono.from(this.dslContext.selectFrom(SECURITY_USER_INVITE)
                .where(SECURITY_USER_INVITE.INVITE_CODE.eq(code))
                .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<Boolean> deleteUserInvitation(String code) {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_USER_INVITE)
                .where(SECURITY_USER_INVITE.INVITE_CODE.eq(code)))
                .map(e -> e == 1);
    }
}
