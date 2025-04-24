package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntityType;
import com.fincity.saas.entity.processor.model.ModelRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Model extends BaseProcessorDto<Model> {

    @Serial
    private static final long serialVersionUID = 3722918782975754023L;

    private Integer dialCode;
    private String phoneNumber;
    private String email;

    public static Model of(ModelRequest modelRequest) {
        return (Model) new Model()
                .setDialCode(modelRequest.getPhoneNumber().getCountryCode())
                .setPhoneNumber(modelRequest.getPhoneNumber().getNumber())
                .setEmail(modelRequest.getEmail().getAddress())
                .setName(modelRequest.getName())
                .setDescription(modelRequest.getDescription());
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.MODEL;
    }
}
