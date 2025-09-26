package com.fincity.saas.entity.processor.analytics.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import com.fincity.saas.entity.processor.util.NameUtil;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

public abstract class BaseAnalyticsService<
                R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>, O extends BaseAnalyticsDAO<R, D>>
        extends AbstractJOOQDataService<R, ULong, D, O> implements IProcessorAccessService {

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

    public Mono<TicketBucketFilter> updateCreatedByIds(ProcessorAccess access, TicketBucketFilter filter) {
        return Mono.just(filter.filterCreatedByIds(access.getUserInherit().getSubOrg()));
    }

    public Mono<TicketBucketFilter> resolveCreatedBys(ProcessorAccess access, TicketBucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> securityService.getUserInternal(
                        access.getUserInherit().getSubOrg().stream()
                                .map(ULong::toBigInteger)
                                .toList(),
                        null),
                userList -> Mono.just(
                        filter.filterCreatedByIds(access.getUserInherit().getSubOrg())
                                .setCreatedBys(userList.stream()
                                        .map(user -> IdAndValue.of(
                                                ULongUtil.valueOf(user.getId()),
                                                NameUtil.assembleFullName(
                                                        user.getFirstName(), user.getMiddleName(), user.getLastName())))
                                        .toList())));
    }

    public Mono<TicketBucketFilter> updateAssignedUserIds(ProcessorAccess access, TicketBucketFilter filter) {
        return Mono.just(filter.filterAssignedUserIds(access.getUserInherit().getSubOrg()));
    }

    public Mono<TicketBucketFilter> resolveAssignedUsers(ProcessorAccess access, TicketBucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> securityService.getUserInternal(
                        access.getUserInherit().getSubOrg().stream()
                                .map(ULong::toBigInteger)
                                .toList(),
                        null),
                userList -> Mono.just(
                        filter.filterAssignedUserIds(access.getUserInherit().getSubOrg())
                                .setAssignedUsers(userList.stream()
                                        .map(user -> IdAndValue.of(
                                                ULongUtil.valueOf(user.getId()),
                                                NameUtil.assembleFullName(
                                                        user.getFirstName(), user.getMiddleName(), user.getLastName())))
                                        .toList())));
    }

    public Mono<TicketBucketFilter> updateClientIds(ProcessorAccess access, TicketBucketFilter filter) {
        return Mono.just(filter.filterClientIds(access.getUserInherit().getManagingClientIds()));
    }

    public Mono<TicketBucketFilter> resolveClients(ProcessorAccess access, TicketBucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> securityService.getClientInternal(
                        access.getUserInherit().getManagingClientIds().stream()
                                .map(ULong::toBigInteger)
                                .toList(),
                        null),
                clientList -> Mono.just(filter.filterClientIds(
                                access.getUserInherit().getManagingClientIds())
                        .setClients(clientList.stream()
                                .map(client -> IdAndValue.of(ULongUtil.valueOf(client.getId()), client.getName()))
                                .toList())));
    }
}
