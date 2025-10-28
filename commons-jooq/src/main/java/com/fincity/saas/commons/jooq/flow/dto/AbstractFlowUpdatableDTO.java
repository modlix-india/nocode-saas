package com.fincity.saas.commons.jooq.flow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.Case;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.commons.util.StringUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class AbstractFlowUpdatableDTO<I extends Serializable, U extends Serializable>
        extends AbstractUpdatableDTO<I, U> implements IClassConvertor {

    public static final String FLOW_NAMESPACE = "FlowSchema";
    private static final UnaryOperator<String> NAMESPACE_CONVERTER = Case.PASCAL.getConverter();

    @Serial
    private static final long serialVersionUID = 295036657353428449L;

    private Map<String, Object> fields;

    public abstract String getTableName();

    @JsonIgnore
    public I getFlowSchemaEntityId() {
        return null;
    }

    public String getServerNameSpace() {
        return null;
    }

    public Schema getSchema() {

        String name = NAMESPACE_CONVERTER.apply(this.getClass().getSimpleName());
        String nameSpace =
                (StringUtil.safeIsBlank(this.getServerNameSpace()) ? FLOW_NAMESPACE : this.getServerNameSpace()) + "."
                        + name;

        Schema schema = Schema.ofObject(name).setNamespace(nameSpace);

        Map<String, Schema> props = new LinkedHashMap<>();
        props.put(Fields.fields, Schema.ofObject(Fields.fields));
        return schema.setProperties(props);
    }
}
