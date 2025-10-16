package com.modlix.saas.commons2.jooq.flow.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.gson.JsonElement;
import com.modlix.saas.commons2.jooq.flow.jackson.FieldSerializer;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import java.io.Serial;
import java.io.Serializable;
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

    @JsonSerialize(using = FieldSerializer.class)
    private JsonElement fields;
}
