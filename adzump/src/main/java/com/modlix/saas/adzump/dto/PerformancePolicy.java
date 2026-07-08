package com.modlix.saas.adzump.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.jooq.enums.AdzumpPerformancePolicyScope;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class PerformancePolicy extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = -5610293847561029385L;

    private String clientCode;
    private AdzumpPerformancePolicyScope scope;
    private ULong campaignId;
    private String vertical;
    private JsonNode body;
}
