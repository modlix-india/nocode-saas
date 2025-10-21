package com.fincity.security.model;

import java.time.LocalDateTime;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ClientPlanRequest {

    private ULong clientId;
    private String urlClientCode;
    private ULong urlClientId;
    private ULong planId;
    private ULong cycleId;
    private LocalDateTime endDate;
}
