package com.fincity.saas.message.eager.relations;

import com.fincity.saas.message.eager.relations.resolvers.RelationResolver;
import java.util.Map;
import org.apache.commons.collections4.SetValuedMap;
import org.jooq.Table;

public interface IRelationMap {

    default Map<String, Table<?>> getRelationsMap() {
        return Map.of();
    }

    default SetValuedMap<Class<? extends RelationResolver>, String> getRelationsResolverMap() {
        return null;
    }
}
