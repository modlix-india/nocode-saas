package com.fincity.saas.entity.collector.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EntityCollectorLog extends AbstractUpdatableDTO<ULong, ULong>  {

    @Serial
    private static final long serialVersionUID = -1027647179030335307L;

    private ULong entityIntegrationId;
    private JsonNode incomingLeadData;
    private String ipAddress;
    private JsonNode outgoingLeadData;
    private EntityCollectorLogStatus status;
    private String statusMessage;



}
