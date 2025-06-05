package com.fincity.security.dao;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.model.condition.*;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.jooq.tables.records.SecurityUserInviteRecord;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;

import static com.fincity.security.jooq.Tables.SECURITY_USER_INVITE;

@Component
public class UserInviteDAO extends AbstractDAO<SecurityUserInviteRecord, ULong, UserInvite> {

    public UserInviteDAO() {
        super(UserInvite.class, SECURITY_USER_INVITE, SECURITY_USER_INVITE.ID);
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

    @Override
    public Mono<Page<UserInvite>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    AbstractCondition newCondition;

                    ULong clientId = ULong.valueOf(ca.getUser().getClientId());

                    if (condition != null) {
                        newCondition = new ComplexCondition()
                                .setOperator(ComplexConditionOperator.AND)
                                .setConditions(List.of(
                                        new FilterCondition()
                                                .setOperator(FilterConditionOperator.EQUALS)
                                                .setField("clientId")
                                                .setValue(clientId),
                                        condition
                                ));
                    } else {
                        newCondition = new FilterCondition()
                                .setOperator(FilterConditionOperator.EQUALS)
                                .setField("clientId")
                                .setValue(clientId);
                    }

                    return super.readPageFilter(pageable, newCondition);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteDAO.readPageFilter"));
    }
}
