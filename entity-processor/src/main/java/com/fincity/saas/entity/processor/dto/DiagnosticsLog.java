package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorDiagnosticsObjectType;
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
public class DiagnosticsLog extends AbstractFlowDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private EntityProcessorDiagnosticsObjectType objectType;
    private ULong objectId;
    private String action;
    private String oldValue;
    private String newValue;
    private String reason;
    private ULong actorId;
    private Map<String, Object> metaData;
}
