package com.fincity.security.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jooq.types.ULong;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ClientActivityDAO;
import com.fincity.security.dto.ClientActivity;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.tables.records.SecurityClientActivityRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientActivityService
        extends AbstractJOOQDataService<SecurityClientActivityRecord, ULong, ClientActivity, ClientActivityDAO> {

    private final ClientService clientService;
    private final UserService userService;
    private final SecurityMessageResourceService securityMessageResourceService;

    public ClientActivityService(@Lazy ClientService clientService, @Lazy UserService userService,
            SecurityMessageResourceService securityMessageResourceService) {
        this.clientService = clientService;
        this.userService = userService;
        this.securityMessageResourceService = securityMessageResourceService;
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser()
                .map(ContextUser::getId)
                .map(ULong::valueOf);
    }

    @Override
    public Mono<ClientActivity> create(ClientActivity entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (entity.getClientId() == null)
                        entity.setClientId(ULongUtil.valueOf(ca.getUser().getClientId()));
                    return Mono.just(entity);
                },

                (ca, e) -> this.clientService.isUserClientManageClient(ca, e.getClientId()),

                (ca, e, hasAccess) -> Boolean.TRUE.equals(hasAccess)
                        ? super.create(e)
                        : this.securityMessageResourceService.<ClientActivity>throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client Activity"))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientActivityService.create"));
    }

    public Mono<Page<ClientActivity>> readPageFilter(ULong clientId, Pageable pageable,
            AbstractCondition extraCondition) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientService.isUserClientManageClient(ca, clientId),

                (ca, hasAccess) -> {

                    if (Boolean.FALSE.equals(hasAccess))
                        return this.securityMessageResourceService.<Page<ClientActivity>>throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client Activity");

                    ULong userClientId = ULongUtil.valueOf(ca.getUser().getClientId());

                    boolean canSeeAll = !userClientId.equals(clientId)
                            || SecurityContextUtil.hasAuthority("Authorities.ROLE_Owner", ca.getAuthorities());

                    List<AbstractCondition> conditions = new ArrayList<>();
                    conditions.add(FilterCondition.make("clientId", clientId));

                    if (!canSeeAll)
                        conditions.add(FilterCondition.make("createdBy",
                                ULongUtil.valueOf(ca.getUser().getId())));

                    if (extraCondition != null && !extraCondition.isEmpty())
                        conditions.add(extraCondition);

                    AbstractCondition scopedCondition = conditions.size() == 1
                            ? conditions.getFirst()
                            : ComplexCondition.and(conditions);

                    return super.readPageFilter(pageable, scopedCondition);
                })

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientActivityService.readPageFilter"));
    }

    public Mono<Void> fillCreatedByUser(List<ClientActivity> activities) {

        if (activities == null || activities.isEmpty())
            return Mono.empty();

        List<ULong> ids = activities.stream()
                .map(ClientActivity::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return Flux.fromIterable(ids)
                .flatMap(userService::readInternal)
                .collectMap(User::getId)
                .doOnNext((Map<ULong, User> userMap) -> activities
                        .forEach(a -> {
                            if (a.getCreatedBy() != null)
                                a.setCreatedByUser(userMap.get(a.getCreatedBy()));
                        }))
                .then();
    }

    public void createLog(ULong clientId, String activityName, String description) {
        ClientActivity activity = new ClientActivity();
        activity.setClientId(clientId);
        activity.setActivityName(activityName);
        activity.setDescription(description);
        super.create(activity).subscribe();
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return this.securityMessageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client Activity Delete");
    }
}
