package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorCollectorLogStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EntityCollectorLog extends AbstractUpdatableDTO<ULong, ULong> implements Serializable {

    @Serial
    private static final long serialVersionUID = 8536402349765874056L;

    private ULong entityIntegrationId;
    private Map<String, Object> incomingEntityData;
    private String ipAddress;
    private Map<String, Object> outgoingEntityData;
    private EntityProcessorCollectorLogStatus status;
    private String statusMessage;
}
