package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import lombok.*;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EntityIntegration extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = -1027647179030335307L;

    private String clientCode;
    private String appCode;
    private String primaryTarget;
    private String secondaryTarget;
    private String inSource;
    private EntityIntegrationsInSourceType inSourceType;
    private String primaryVerifyToken;
    private String secondaryVerifyToken;
}
