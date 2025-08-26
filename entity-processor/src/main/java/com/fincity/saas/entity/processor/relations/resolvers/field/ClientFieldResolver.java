package com.fincity.saas.entity.processor.relations.resolvers.field;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ClientFieldResolver implements RelationResolver {

    private static final Set<String> SUPPORTED_FIELDS = Set.of("clientId");

    private final IFeignSecurityService securityService;

    public ClientFieldResolver(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public Set<String> getFields() {
        return SUPPORTED_FIELDS;
    }

    @Override
    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(Set<ULong> idsToResolve) {
        return null;
    }

    @Override
    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(Set<ULong> idsToResolve, List<String> eagerFields) {
        return null;
    }
}
