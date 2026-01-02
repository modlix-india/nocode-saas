package com.fincity.saas.entity.processor.eager.relations.resolvers.field;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolver;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DesignationFieldResolver implements RelationResolver {

    private static final Set<String> SUPPORTED_FIELDS = Set.of("designationId");

    private final IFeignSecurityService securityService;

    public DesignationFieldResolver(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public Set<String> getFields(){
        return SUPPORTED_FIELDS;
    }

    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(
            Set<ULong> idsToResolve, MultiValueMap<String, String> queryParams) {
        if (idsToResolve == null || idsToResolve.isEmpty()) return Mono.just(Map.of());

        Boolean eager = EagerUtil.getIsEagerParams(queryParams);
        List<String> eagerFields = EagerUtil.getEagerParams(queryParams);

        if (idsToResolve.size() == 1)
            return this.securityService
                    .getDesignationInternal(idsToResolve.iterator().next().toBigInteger(), queryParams)
                    .map(designation -> Map.of(ULongUtil.valueOf(designation.getId()), designation.toMap()))
                    .flatMap(designationMap -> this.applyEagerFiltering(designationMap, eager, eagerFields));
        return this.securityService
                .getDesignationsInternal(
                        idsToResolve.stream().map(ULong::toBigInteger).toList(), queryParams)
                .map(designationList -> designationList.stream()
                        .collect(Collectors.toMap(designation -> ULongUtil.valueOf(designation.getId()), IClassConvertor::toMap)))
                .flatMap(designationMap -> this.applyEagerFiltering(designationMap, eager, eagerFields));
    }
}
