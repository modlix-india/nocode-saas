package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.relations.IRelationMap;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.relations.resolvers.UserFieldResolver;
import com.fincity.saas.entity.processor.util.IClassConvertor;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.jooq.Table;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class BaseDto<T extends BaseDto<T>> extends AbstractFlowDTO<ULong, ULong>
        implements IClassConvertor, IEntitySeries, IRelationMap {

    public static final int CODE_LENGTH = 22;

    @Serial
    private static final long serialVersionUID = 717874495505090612L;

    @JsonIgnore
    protected transient Map<String, Table<?>> relationsMap = new HashMap<>();

    @JsonIgnore
    protected transient SetValuedMap<Class<? extends RelationResolver>, String> relationsResolverMap =
            new HashSetValuedHashMap<>();

    private String code = UniqueUtil.shortUUID();

    private String name = this.code;
    private String description;

    protected BaseDto() {
        super();
        this.relationsResolverMap.put(UserFieldResolver.class, AbstractDTO.Fields.createdBy);
        this.relationsResolverMap.put(UserFieldResolver.class, AbstractUpdatableDTO.Fields.updatedBy);
    }

    public T setCode() {
        if (this.code != null) return (T) this;
        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }

    public T setName(String name) {
        if (name == null || name.isBlank()) this.name = this.getCode();
        else this.name = name;
        return (T) this;
    }

    public T setDescription(String description) {
        if (description == null || description.isBlank()) return (T) this;
        this.description = description;
        return (T) this;
    }

    public BaseResponse toBaseResponse() {
        return BaseResponse.of(this.getId(), this.code, this.name);
    }
}
