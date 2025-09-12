package com.modlix.saas.commons2.jooq.flow.dto.schema;

import java.io.Serializable;

import org.springframework.data.annotation.Version;

import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

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

    @Version
    private int version = 1;

    private String appCode;
    private String clientCode;
    private String tableType;
    private String tableCode;
    private String tableSchema;
}
