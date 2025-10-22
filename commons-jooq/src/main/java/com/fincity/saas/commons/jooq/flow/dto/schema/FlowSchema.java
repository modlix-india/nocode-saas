package com.fincity.saas.commons.jooq.flow.dto.schema;

import java.io.Serializable;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

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
public abstract class FlowSchema<I extends Serializable, U extends Serializable> extends AbstractUpdatableDTO<I, U> {

    @Version
    private int version = 1;

    private String dbSchema;
    private String dbTableName;
    private ULong dbId;
    private Map<String, Object> fieldSchema;

    protected FlowSchema() {
        super();
    }
}
