package com.fincity.security.dao;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.jooq.tables.records.SecurityUserInviteRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
}
