package com.fincity.saas.commons.jooq.flow.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fincity.saas.commons.jooq.flow.jackson.FieldDeserializer;
import com.fincity.saas.commons.jooq.flow.jackson.FieldSerializer;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
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
public abstract class AbstractFlowDTO<I extends Serializable, U extends Serializable> extends AbstractDTO<I, U> {

    @Serial
    private static final long serialVersionUID = 7121981370061595384L;

    @JsonDeserialize(using = FieldDeserializer.class)
    @JsonSerialize(using = FieldSerializer.class)
    private Map<String, Object> fields;
}
