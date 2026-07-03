package com.modlix.saas.adzump.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.jooq.enums.AdzumpMilestoneMappingScope;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class MilestoneMapping extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = -1029384756102938476L;

    private String clientCode;
    private AdzumpMilestoneMappingScope scope;
    private ULong campaignId;
    private String productTemplateId;
    private JsonNode body;
}
