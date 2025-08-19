package com.fincity.saas.entity.processor.analytics.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.model.BucketFilter;
import com.fincity.saas.entity.processor.analytics.model.StatusCount;
import com.fincity.saas.entity.processor.analytics.service.base.BaseAnalyticsService;
import com.fincity.saas.entity.processor.analytics.util.ReactivePaginationUtil;
import com.fincity.saas.entity.processor.analytics.util.ReportUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class TicketBucketService extends BaseAnalyticsService<EntityProcessorTicketsRecord, Ticket, TicketBucketDAO> {

    public Mono<Page<StatusCount>> getTicketPerAssignedUserStatusCount(Pageable pageable, BucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.resolveUsersIds(access, filter),
                (access, userFilter) -> this.resolveStages(access, userFilter.getT1()),
                (access, userFilter, stageFilter) -> this.dao
                        .getTicketPerAssignedUserStageCount(access, stageFilter.getT1())
                        .collectList(),
                (access, userFilter, stageFilter, perStageCount) -> ReportUtil.toStatusCounts(
                                perStageCount,
                                userFilter.getT2(),
                                IdAndValue.toValueList(stageFilter.getT2()),
                                Boolean.TRUE)
                        .collectList(),
                (access, userFilter, stageFilter, perStageCount, perStatusCount) ->
                        ReactivePaginationUtil.toPage(perStatusCount, pageable));
    }

    public Mono<Tuple2<BucketFilter, List<IdAndValue<ULong, String>>>> resolveStages(
            ProcessorAccess access, BucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.getValuesFlat(
                        access.getAppCode(),
                        access.getClientCode(),
                        null,
                        null,
                        Boolean.TRUE,
                        filter.getStageIds().toArray(new ULong[0])),
                stages -> Mono.just(Tuples.of(
                        filter.setStageIds(
                                stages.stream().map(BaseUpdatableDto::getId).toList()),
                        stages.stream()
                                .map(stage -> IdAndValue.of(stage.getId(), stage.getName()))
                                .toList())));
    }

    public Mono<Tuple2<BucketFilter, List<IdAndValue<ULong, String>>>> resolveUsersIds(
            ProcessorAccess access, BucketFilter filter) {

        if (access.getSubOrg().size() == 1)
            return FlatMapUtil.flatMapMono(
                    () -> this.securityService.getUserInternal(
                            access.getSubOrg().getFirst().toBigInteger()),
                    userResponse -> Mono.just(Tuples.of(
                            filter.filterUserIds(List.of(access.getSubOrg().getFirst())),
                            List.of(IdAndValue.of(
                                    access.getSubOrg().getFirst(),
                                    NameUtil.assembleFullName(
                                            userResponse.getFirstName(),
                                            userResponse.getMiddleName(),
                                            userResponse.getLastName()))))));

        return FlatMapUtil.flatMapMono(
                () -> securityService.getUserInternal(
                        access.getSubOrg().stream().map(ULong::toBigInteger).toList()),
                userList -> Mono.just(Tuples.of(
                        filter.filterUserIds(access.getSubOrg()),
                        userList.stream()
                                .map(userResponse -> IdAndValue.of(
                                        ULongUtil.valueOf(userResponse.getId()),
                                        NameUtil.assembleFullName(
                                                userResponse.getFirstName(),
                                                userResponse.getMiddleName(),
                                                userResponse.getLastName())))
                                .toList())));
    }
}
