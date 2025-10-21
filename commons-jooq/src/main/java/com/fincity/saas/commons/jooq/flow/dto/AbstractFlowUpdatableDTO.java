package com.fincity.saas.commons.jooq.flow.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fincity.saas.commons.jooq.flow.jackson.FieldDeserializer;
import com.fincity.saas.commons.jooq.flow.jackson.FieldSerializer;
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
public abstract class AbstractFlowUpdatableDTO<I extends Serializable, U extends Serializable>
        extends AbstractUpdatableDTO<I, U> {

    @Serial
    private static final long serialVersionUID = 295036657353428449L;

    @JsonDeserialize(using = FieldDeserializer.class)
    @JsonSerialize(using = FieldSerializer.class)
    private Map<String, Object> fields;

    public String getDbTableName() {
        return null;
    }
}
