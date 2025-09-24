package com.fincity.saas.entity.processor.analytics.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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

    public <T> Mono<Tuple2<T, List<IdAndValue<ULong, String>>>> resolveUserIds(
            ProcessorAccess access, Function<List<ULong>, T> function) {

        return FlatMapUtil.flatMapMono(
                () -> securityService.getUserInternal(
                        access.getUserInherit().getSubOrg().stream()
                                .map(ULong::toBigInteger)
                                .toList(),
                        null),
                userList -> Mono.just(Tuples.of(
                        function.apply(access.getUserInherit().getSubOrg()),
                        userList.stream()
                                .map(user -> IdAndValue.of(
                                        ULongUtil.valueOf(user.getId()),
                                        NameUtil.assembleFullName(
                                                user.getFirstName(), user.getMiddleName(), user.getLastName())))
                                .toList())));
    }

    public <T> Mono<Tuple2<T, List<IdAndValue<ULong, String>>>> resolveClientIds(
            ProcessorAccess access, Function<List<ULong>, T> function) {

        return FlatMapUtil.flatMapMono(
                () -> securityService.getClientInternal(
                        access.getUserInherit().getManagingClientIds().stream()
                                .map(ULong::toBigInteger)
                                .toList(),
                        null),
                clientList -> Mono.just(Tuples.of(
                        function.apply(access.getUserInherit().getManagingClientIds()),
                        clientList.stream()
                                .map(client -> IdAndValue.of(ULongUtil.valueOf(client.getId()), client.getName()))
                                .toList())));
    }
}
