package com.fincity.saas.entity.processor.eager.relations.resolvers.field;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

@Component
public class DepartmentFieldResolver implements RelationResolver {
    private static final Set<String> SUPPORTED_FIELDS = Set.of("departmentId");

    private final IFeignSecurityService securityService;

    public DepartmentFieldResolver(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public Set<String> getFields() {
        return SUPPORTED_FIELDS;
    }

    @Override
    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(
            Set<ULong> idsToResolver, MultiValueMap<String, String> queryParams) {

        if (idsToResolver == null || idsToResolver.isEmpty()) return Mono.just(Map.of());

        Boolean eager = EagerUtil.getIsEagerParams(queryParams);
        List<String> eagerFields = EagerUtil.getEagerParams(queryParams);

        if (idsToResolver.size() == 1)
            return this.securityService
                    .getDepartmentInternal(idsToResolver.iterator().next().toBigInteger(), queryParams)
                    .map(department -> Map.of(ULongUtil.valueOf(department.getId()), department.toMap()))
                    .flatMap(departmentMap -> this.applyEagerFiltering(departmentMap, eager, eagerFields));

        return this.securityService
                .getDepartmentInternal(
                        idsToResolver.stream().map(ULong::toBigInteger).toList(), queryParams)
                .map(departmentList -> departmentList.stream()
                        .collect(Collectors.toMap(
                                department -> ULongUtil.valueOf(department.getId()), IClassConvertor::toMap)))
                .flatMap(departmentMap -> this.applyEagerFiltering(departmentMap, eager, eagerFields));
    }
}
