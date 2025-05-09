package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.EntityRequest;
import com.fincity.saas.entity.processor.util.PhoneUtil;
import java.io.Serial;
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
public class Entity extends BaseProcessorDto<Entity> {

    @Serial
    private static final long serialVersionUID = 1639822311147907381L;

    private ULong modelId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private ULong productId;
    private String stage;
    private String status;
    private String source;
    private String subSource;

    public static Entity of(EntityRequest entityRequest) {
        return new Entity()
                .setDialCode(entityRequest.getPhoneNumber().getCountryCode())
                .setPhoneNumber(entityRequest.getPhoneNumber().getNumber())
                .setEmail(entityRequest.getEmail().getAddress())
                .setSource(entityRequest.getSource())
                .setSubSource(entityRequest.getSubSource() != null ? entityRequest.getSubSource() : null)
                .setName(entityRequest.getName())
                .setDescription(entityRequest.getDescription());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.ENTITY;
    }
}
