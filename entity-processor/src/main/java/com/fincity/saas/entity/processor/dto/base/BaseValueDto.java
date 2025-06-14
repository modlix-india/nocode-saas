package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import java.io.Serial;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class BaseValueDto<T extends BaseValueDto<T>> extends BaseUpdatableDto<T> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 2090745028406660414L;

    private Platform platform = Platform.PRE_QUALIFICATION;
    private ULong productTemplateId;
    private Boolean isParent;
    private ULong parentLevel0;
    private ULong parentLevel1;
    private Integer order;

    public T setOrder(Integer order) {
        this.order = order;
        return (T) this;
    }

    public boolean hasParentLevels() {
        return this.parentLevel0 != null || this.parentLevel1 != null;
    }

    public boolean isChild() {
        return !this.isParent;
    }

    @JsonIgnore
    public boolean inFamily(ULong childId) {
        return this.getId().equals(childId) || this.parentLevel0.equals(childId) || this.parentLevel1.equals(childId);
    }

    @JsonIgnore
    public boolean isValidChild(ULong... childIds) {
        return Stream.of(childIds).anyMatch(childId -> this.getId().equals(childId));
    }

    // 	These Methods are for JOOQ Compatibility.
    // 	Jooq uses {@code org.jooq.tools.StringUtils.toCamelCase()} to get getter and setter of a Entity
    @JsonIgnore
    public ULong getParentLevel_0() {
        return this.parentLevel0;
    }

    public ULong setParentLevel_0(ULong parentLevel0) {
        this.parentLevel0 = parentLevel0;
        return parentLevel0;
    }

    @JsonIgnore
    public ULong getParentLevel_1() {
        return this.parentLevel1;
    }

    public ULong setParentLevel_1(ULong parentLevel1) {
        this.parentLevel1 = parentLevel1;
        return parentLevel1;
    }
}
