package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import java.io.Serial;
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
public abstract class BaseValueDto<T extends BaseValueDto<T>> extends BaseUpdatableDto<T> {

    @Serial
    private static final long serialVersionUID = 2090745028406660414L;

    private Platform platform = Platform.PRE_QUALIFICATION;
    private ULong productTemplateId;
    private Boolean isParent;
    private ULong parentLevel0;
    private ULong parentLevel1;
    private Integer order;

    protected BaseValueDto() {
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

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
    public boolean hasParent(ULong parentId) {

        if (this.parentLevel0 == null && this.parentLevel1 == null)
            return false;

        boolean hasParentLevel0 = this.parentLevel0 != null && this.parentLevel0.equals(parentId);
        boolean hasParentLevel1 = this.parentLevel1 != null && this.parentLevel1.equals(parentId);

        return hasParentLevel0 || hasParentLevel1;
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
