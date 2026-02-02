package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import java.io.Serial;

import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsStatus;
import lombok.*;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EntityIntegration extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 774068654647877436L;

    private String clientCode;
    private String inAppCode;
    private String outAppCode;
    private String primaryTarget;
    private String secondaryTarget;
    private String inSource;
    private EntityIntegrationsInSourceType inSourceType;
    private String primaryVerifyToken;
    private String secondaryVerifyToken;
    private EntityIntegrationsStatus status;
}
