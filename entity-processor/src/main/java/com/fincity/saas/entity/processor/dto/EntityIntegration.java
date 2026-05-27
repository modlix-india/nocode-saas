package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorIntegrationsInSourceType;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorIntegrationsStatus;
import java.io.Serial;
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
    private EntityProcessorIntegrationsInSourceType inSourceType;
    private String primaryVerifyToken;
    private String secondaryVerifyToken;
    private EntityProcessorIntegrationsStatus status;
}
