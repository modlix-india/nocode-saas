package com.fincity.security.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.UserInviteDAO;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.jooq.tables.records.SecurityUserInviteRecord;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.UserRegistrationRequest;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

@Service
public class UserInviteService extends AbstractJOOQDataService<SecurityUserInviteRecord, ULong, UserInvite, UserInviteDAO> {

    private final SecurityMessageResourceService msgService;
    private final ClientService clientService;
    private final UserService userService;

    public UserInviteService(SecurityMessageResourceService msgService, ClientService clientService, UserService userService) {

        this.msgService = msgService;
        this.clientService = clientService;
        this.userService = userService;
    }

    @PreAuthorize("hasAuthority('Authorities.User_CREATE')")
    @Override
    public Mono<UserInvite> create(UserInvite entity) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> {
                            if (entity.getClientId() == null) {
                                entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
                                return Mono.just(entity);
                            }

                            return this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), entity.getClientId())
                                    .filter(BooleanUtil::safeValueOf)
                                    .map(x -> entity);
                        },

                        (ca, invite) -> {
                            invite.setInviteCode(UUID.randomUUID().toString().replace("-", ""));
                            return super.create(invite);
                        }
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.create"))
                .switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE, "User Invite"));
    }

    public Mono<UserInvite> getUserInvitation(String code) {
        return this.dao.getUserInvitation(code);
    }

    public Mono<Boolean> deleteUserInvitation(String code) {
        return this.dao.deleteUserInvitation(code);
    }

    public Mono<RegistrationResponse> acceptInvite(UserRegistrationRequest userRequest) {
        return FlatMapUtil.flatMapMono(

                        () -> this.dao.getUserInvitation(userRequest.getInviteCode()),

                        userInvite -> this.userService.createWithInvitation(userRequest, userInvite)
                ).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.acceptInvite"))
                .switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE, "User Invitation Error"));
    }
}
