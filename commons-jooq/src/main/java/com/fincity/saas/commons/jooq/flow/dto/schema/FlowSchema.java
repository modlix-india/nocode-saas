package com.fincity.saas.commons.jooq.flow.dto.schema;

import java.io.Serial;
import java.io.Serializable;

import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

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
public class FlowSchema<I extends Serializable, U extends Serializable> extends AbstractUpdatableDTO<I, U> {

	@Serial
	private static final long serialVersionUID = 1641571484187027769L;

    @Version
    private int version = 1;

    private String appCode;
    private String clientCode;
    private String serverPrefix;
    private String tableName;
    private String fieldName;
    private Schema fieldSchema;
    private boolean signed;
    private boolean isUnsigned;
    private boolean isNullable;
    private boolean isUnique;
    private ULong relationFieldId;
    private String comment;

    public FlowSchema() {
        super();
    }
}
