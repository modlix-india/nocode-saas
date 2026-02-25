package com.fincity.saas.entity.processor.analytics.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.util.CollectionUtil;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public abstract class BaseAnalyticsService<
                R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>, O extends BaseAnalyticsDAO<R, D>>
        extends AbstractJOOQDataService<R, ULong, D, O> implements IProcessorAccessService, IEntitySeries {

    @Getter
    protected IFeignSecurityService securityService;

    @Getter
    protected ProcessorMessageResourceService msgService;

    protected StageService stageService;

    protected ProductService productService;

    @Autowired
    private void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    private void setMsgService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    public Mono<TicketBucketFilter> resolveCreatedBys(ProcessorAccess access, TicketBucketFilter filter) {

        List<BigInteger> createdBysIds =
                CollectionUtil.intersectLists(
                                filter.getCreatedByIds(),
                                access.getUserInherit().getManagingClientIds())
                        .stream()
                        .map(ULong::toBigInteger)
                        .toList();

        if (createdBysIds.isEmpty()) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> securityService.getClientUserInternalBatch(createdBysIds, null),
                userList -> Mono.just(filter.filterCreatedByIds(userList.stream()
                                .map(User::getId)
                                .map(ULongUtil::valueOf)
                                .toList())
                        .setCreatedBys(userList.stream()
                                .map(user -> IdAndValue.of(
                                        ULongUtil.valueOf(user.getId()),
                                        NameUtil.assembleFullName(
                                                user.getFirstName(), user.getMiddleName(), user.getLastName())))
                                .toList())));
    }

    public Mono<TicketBucketFilter> resolveAssignedUsers(ProcessorAccess access, TicketBucketFilter filter) {

        List<BigInteger> assignedUsersIds =
                CollectionUtil.intersectLists(
                                filter.getAssignedUserIds(),
                                access.getUserInherit().getSubOrg())
                        .stream()
                        .map(ULong::toBigInteger)
                        .toList();

        if (assignedUsersIds.isEmpty()) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> securityService.getUsersInternalBatch(assignedUsersIds, null),
                userList -> Mono.just(
                        filter.filterAssignedUserIds(access.getUserInherit().getSubOrg())
                                .setAssignedUsers(userList.stream()
                                        .map(user -> IdAndValue.of(
                                                ULongUtil.valueOf(user.getId()),
                                                NameUtil.assembleFullName(
                                                        user.getFirstName(), user.getMiddleName(), user.getLastName())))
                                        .toList())));
    }

    public Mono<TicketBucketFilter> resolveClients(ProcessorAccess access, TicketBucketFilter filter) {

        List<ULong> clientIds = CollectionUtil.intersectLists(
                filter.getClientIds(), access.getUserInherit().getManagingClientIds());

        if (clientIds.isEmpty()) return Mono.empty();

        return applyClientManagerFilter(clientIds, filter).flatMap(ids -> fetchClientsAndApplyToFilter(ids, filter));
    }

    private Mono<List<ULong>> applyClientManagerFilter(List<ULong> clientIds, TicketBucketFilter filter) {

        if (filter.getClientManagerIds() == null || filter.getClientManagerIds().isEmpty()) return Mono.just(clientIds);

        List<BigInteger> managerIds =
                filter.getClientManagerIds().stream().map(ULong::toBigInteger).toList();

        return securityService.getClientIdsOfManagers(managerIds).map(managerClientIds -> {
            Set<BigInteger> set = new HashSet<>(managerClientIds);
            return clientIds.stream()
                    .filter(id -> set.contains(id.toBigInteger()))
                    .toList();
        });
    }

    private Mono<TicketBucketFilter> fetchClientsAndApplyToFilter(
            List<ULong> resolvedClientIds, TicketBucketFilter filter) {

        if (resolvedClientIds.isEmpty()) return Mono.empty();

        List<BigInteger> idsBigInt =
                resolvedClientIds.stream().map(ULong::toBigInteger).toList();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("fetchClientManagers", "true");

        return securityService
                .getClientInternalBatch(idsBigInt, params)
                .map(clientList -> buildResolvedClientFilter(clientList, filter, resolvedClientIds));
    }

    private TicketBucketFilter buildResolvedClientFilter(
            List<Client> clientList, TicketBucketFilter filter, List<ULong> resolvedClientIds) {

        int size = clientList.size();
        List<IdAndValue<ULong, String>> clients = new ArrayList<>(size);
        Map<ULong, List<IdAndValue<ULong, String>>> clientManagersByClientId =
                LinkedHashMap.newLinkedHashMap((int) (size / 0.75f) + 1);

        for (Client client : clientList) {
            ULong clientId = ULongUtil.valueOf(client.getId());
            clients.add(IdAndValue.of(clientId, client.getName()));
            List<IdAndValue<ULong, String>> managers = toManagerIdAndValues(client.getClientManagers());
            clientManagersByClientId.put(clientId, managers);
        }

        return filter.filterClientIds(resolvedClientIds)
                .setClients(clients)
                .setClientManagersByClientId(clientManagersByClientId);
    }

    private static List<IdAndValue<ULong, String>> toManagerIdAndValues(List<User> clientManagers) {

        if (clientManagers == null || clientManagers.isEmpty()) return List.of();

        return clientManagers.stream()
                .map(u -> IdAndValue.of(
                        ULongUtil.valueOf(u.getId()),
                        NameUtil.assembleFullName(u.getFirstName(), u.getMiddleName(), u.getLastName())))
                .toList();
    }
}
