package com.fincity.saas.entity.processor.analytics.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.analytics.model.BucketFilter;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.util.List;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class BaseAnalyticsService<
                R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>, O extends AbstractDAO<R, ULong, D>>
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

    public Mono<Tuple2<BucketFilter, List<IdAndValue<ULong, String>>>> resolveAssignedUserIds(
            ProcessorAccess access, BucketFilter filter) {

        if (access.getUserInherit().getSubOrg().size() == 1)
            return FlatMapUtil.flatMapMono(
                    () -> this.securityService.getUserInternal(
                            access.getUserInherit().getSubOrg().getFirst().toBigInteger(), null),
                    user -> Mono.just(Tuples.of(
                            filter.filterAssignedUserIds(
                                    List.of(access.getUserInherit().getSubOrg().getFirst())),
                            List.of(IdAndValue.of(
                                    access.getUserInherit().getSubOrg().getFirst(),
                                    NameUtil.assembleFullName(
                                            user.getFirstName(), user.getMiddleName(), user.getLastName()))))));

        return FlatMapUtil.flatMapMono(
                () -> securityService.getUserInternal(
                        access.getUserInherit().getSubOrg().stream()
                                .map(ULong::toBigInteger)
                                .toList(),
                        null),
                userList -> Mono.just(Tuples.of(
                        filter.filterAssignedUserIds(access.getUserInherit().getSubOrg()),
                        userList.stream()
                                .map(user -> IdAndValue.of(
                                        ULongUtil.valueOf(user.getId()),
                                        NameUtil.assembleFullName(
                                                user.getFirstName(), user.getMiddleName(), user.getLastName())))
                                .toList())));
    }
}
