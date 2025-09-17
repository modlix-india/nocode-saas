package com.modlix.saas.commons2.jooq.flow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.modlix.saas.commons2.jooq.flow.FlowField;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class AbstractFlowUpdatableDTO<I extends Serializable, U extends Serializable>
        extends AbstractUpdatableDTO<I, U> {

    @Serial
    private static final long serialVersionUID = 295036657353428449L;

    private String appCode;
    private String clientCode;

    @FlowField
    @JsonIgnore
    private JsonObject fields;
}
