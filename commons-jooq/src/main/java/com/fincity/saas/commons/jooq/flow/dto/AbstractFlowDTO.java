package com.fincity.saas.commons.jooq.flow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.commons.model.dto.AbstractDTO;
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
public abstract class AbstractFlowDTO<I extends Serializable, U extends Serializable> extends AbstractDTO<I, U>
        implements IClassConvertor {

    public static final String FLOW_NAMESPACE = DbSchema.DB_NAMESPACE + "." + "Flow";
    private static final UnaryOperator<String> NAMESPACE_CONVERTER = Case.PASCAL.getConverter();

    @Serial
    private static final long serialVersionUID = 7121981370061595384L;

    private Map<String, Object> fields;

    public abstract String getTableName();

    @JsonIgnore
    public I getFlowSchemaEntityId() {
        return null;
    }

    public String getServerNameSpace() {
        return null;
    }

	public final Schema getSchema() {
		Schema schema = this.createBaseSchema();
		this.extendSchema(schema);
		return schema;
	}

	protected Schema createBaseSchema() {
		String name = NAMESPACE_CONVERTER.apply(this.getClass().getSimpleName());
		String nameSpace = StringUtil.safeIsBlank(this.getServerNameSpace())
				? FLOW_NAMESPACE
				: this.getServerNameSpace() + "." + FLOW_NAMESPACE + "." + name;

		Schema schema = Schema.ofObject(name).setNamespace(nameSpace);

		Map<String, Schema> props = new LinkedHashMap<>();
		props.put(AbstractFlowUpdatableDTO.Fields.fields, DbSchema.ofJson(AbstractFlowUpdatableDTO.Fields.fields));
		return schema.setProperties(props);
	}

	protected void extendSchema(Schema schema) {}
}
