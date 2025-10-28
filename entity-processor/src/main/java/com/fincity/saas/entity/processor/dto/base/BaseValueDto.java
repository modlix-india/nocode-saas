package com.fincity.saas.entity.processor.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.enums.Platform;
import com.google.gson.JsonPrimitive;
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
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();

        props.put(
                Fields.platform,
                Schema.ofString(Fields.platform).setEnums(EnumSchemaUtil.getSchemaEnums(Platform.class)));
        props.put(
                Fields.productTemplateId,
                Schema.ofLong(Fields.productTemplateId).setMinimum(1));
        props.put(Fields.isParent, Schema.ofBoolean(Fields.isParent).setDefaultValue(new JsonPrimitive(true)));
        props.put(Fields.parentLevel0, Schema.ofLong(Fields.parentLevel0).setMinimum(1));
        props.put(Fields.parentLevel1, Schema.ofLong(Fields.parentLevel1).setMinimum(1));
        props.put(Fields.order, Schema.ofInteger(Fields.order).setMinimum(0));

        schema.setProperties(props);
        return schema;
    }
}
