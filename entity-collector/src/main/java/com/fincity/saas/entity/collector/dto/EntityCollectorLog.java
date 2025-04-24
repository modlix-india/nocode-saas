package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@Accessors(chain = true)
public class EntityCollectorLog implements Serializable {

    @Serial
    private static final long serialVersionUID = -1027647179030335307L;

    private ULong id;
    private ULong entityIntegrationId;
    private Map<String, Object> incomingEntityData;
    private String ipAddress;
    private Map<String, Object> outgoingEntityData;
    private EntityCollectorLogStatus status;
    private String statusMessage;

}