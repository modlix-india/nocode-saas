package com.fincity.saas.entity.processor.analytics.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import com.fincity.saas.entity.processor.util.FilterUtil;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
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
                FilterUtil.intersectLists(
                                filter.getCreatedByIds(),
                                access.getUserInherit().getManagingClientIds())
                        .stream()
                        .map(ULong::toBigInteger)
                        .toList();

        if (createdBysIds.isEmpty()) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> securityService.getClientUserInternal(
                        createdBysIds,
                        new LinkedMultiValueMap<>(
                                Map.of(BaseUpdatableDto.Fields.appCode, List.of(access.getAppCode())))),
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
                FilterUtil.intersectLists(
                                filter.getAssignedUserIds(),
                                access.getUserInherit().getSubOrg())
                        .stream()
                        .map(ULong::toBigInteger)
                        .toList();

        if (assignedUsersIds.isEmpty()) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> securityService.getUsersInternal(assignedUsersIds, null),
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

        List<BigInteger> clientIds =
                FilterUtil.intersectLists(
                                filter.getClientIds(), access.getUserInherit().getManagingClientIds())
                        .stream()
                        .map(ULong::toBigInteger)
                        .toList();

        if (clientIds.isEmpty()) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> securityService.getClientInternal(clientIds, null),
                clientList -> Mono.just(filter.filterClientIds(
                                access.getUserInherit().getManagingClientIds())
                        .setClients(clientList.stream()
                                .map(client -> IdAndValue.of(ULongUtil.valueOf(client.getId()), client.getName()))
                                .toList())));
    }
}
