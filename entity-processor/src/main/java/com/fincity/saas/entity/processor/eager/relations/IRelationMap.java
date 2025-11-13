package com.fincity.saas.entity.processor.eager.relations;

import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolver;
import java.util.Map;
import org.apache.commons.collections4.SetValuedMap;
import org.jooq.Table;

public interface IRelationMap {

    Map<String, Table<?>> getRelationsMap();

    SetValuedMap<Class<? extends RelationResolver>, String> getRelationsResolverMap();
}
