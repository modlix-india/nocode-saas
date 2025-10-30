package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import java.io.Serial;
import java.util.Map;
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
    private Boolean isParent = Boolean.TRUE;
    private ULong parentLevel0;
    private ULong parentLevel1;
    private Integer order;

    protected BaseValueDto() {
        super();
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

    protected BaseValueDto(BaseValueDto<T> baseValueDto) {
        super(baseValueDto);
        this.platform = baseValueDto.platform;
        this.productTemplateId = baseValueDto.productTemplateId;
        this.isParent = baseValueDto.isParent;
        this.parentLevel0 = baseValueDto.parentLevel0;
        this.parentLevel1 = baseValueDto.parentLevel1;
        this.order = baseValueDto.order;
    }

    @SuppressWarnings("unchecked")
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

        if (this.parentLevel0 == null && this.parentLevel1 == null) return false;

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

    @Override
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.platform, DbSchema.ofEnum(Fields.platform, Platform.class));
        props.put(Fields.productTemplateId, DbSchema.ofNumberId(Fields.productTemplateId));
        props.put(Fields.isParent, DbSchema.ofBooleanTrue(Fields.isParent));
        props.put(Fields.parentLevel0, DbSchema.ofNumberId(Fields.parentLevel0));
        props.put(Fields.parentLevel1, DbSchema.ofNumberId(Fields.parentLevel1));
        props.put(Fields.order, DbSchema.ofInt(Fields.order).setMinimum(0));

        schema.setProperties(props);
    }
}
