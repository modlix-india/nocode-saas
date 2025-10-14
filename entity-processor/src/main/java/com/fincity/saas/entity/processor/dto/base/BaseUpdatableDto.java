package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.relations.IRelationMap;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.relations.resolvers.field.UserFieldResolver;
import java.io.Serial;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
public abstract class BaseUpdatableDto<T extends BaseUpdatableDto<T>> extends AbstractFlowUpdatableDTO<ULong, ULong>
        implements IClassConvertor, IEntitySeries, IRelationMap {

    public static final int CODE_LENGTH = 22;

    @Serial
    private static final long serialVersionUID = 1844345864104376760L;

    @JsonIgnore
    protected transient Map<String, Table<?>> relationsMap = new HashMap<>();

    @JsonIgnore
    protected transient SetValuedMap<Class<? extends RelationResolver>, String> relationsResolverMap =
            new HashSetValuedHashMap<>();

    private String code = UniqueUtil.shortUUID();
    private String name;
    private String description;

    @JsonIgnore
    private boolean tempActive = Boolean.FALSE;

    private boolean isActive = Boolean.TRUE;

    protected BaseUpdatableDto() {
        super();
        this.relationsResolverMap.put(UserFieldResolver.class, AbstractDTO.Fields.createdBy);
        this.relationsResolverMap.put(UserFieldResolver.class, AbstractUpdatableDTO.Fields.updatedBy);
    }

    protected BaseUpdatableDto(BaseUpdatableDto<T> baseUpdatableDto) {

        super();

        this.setId(baseUpdatableDto.getId());
        this.setCreatedAt(baseUpdatableDto.getCreatedAt());
        this.setCreatedBy(baseUpdatableDto.getCreatedBy());
        this.setUpdatedAt(baseUpdatableDto.getUpdatedAt());
        this.setUpdatedBy(baseUpdatableDto.getUpdatedBy());

        this.setAppCode(baseUpdatableDto.getAppCode());
        this.setClientCode(baseUpdatableDto.getClientCode());

        this.code = baseUpdatableDto.code;
        this.name = baseUpdatableDto.name;
        this.description = baseUpdatableDto.description;
        this.tempActive = baseUpdatableDto.tempActive;
        this.isActive = baseUpdatableDto.isActive;
    }

    public static <T extends BaseUpdatableDto<T>> Map<ULong, T> toIdMap(Collection<T> baseDtoList) {
        return baseDtoList.stream()
                .collect(Collectors.toMap(BaseUpdatableDto::getId, Function.identity(), (a, b) -> b));
    }

    public T setCode() {
        if (this.code != null) return (T) this;
        this.code = UniqueUtil.shortUUID();
        return (T) this;
    }

    public T setName(String name) {
        this.name = name;
        return (T) this;
    }

    public T setDescription(String description) {
        this.description = description;
        return (T) this;
    }

    public T setActive(boolean isActive) {
        this.isActive = isActive;
        return (T) this;
    }

    public T setTempActive(boolean tempActive) {
        this.tempActive = tempActive;
        return (T) this;
    }

    @JsonIgnore
    public BaseResponse getBaseResponse() {
        return BaseResponse.of(this.getId(), this.code, this.name);
    }

    @JsonIgnore
    public Identity getIdentity() {
        return Identity.of(this.getId().toBigInteger(), this.getCode());
    }
}
