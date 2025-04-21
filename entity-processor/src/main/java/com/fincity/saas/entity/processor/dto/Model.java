package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import com.fincity.saas.entity.processor.model.ModelRequest;

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
    private String source;
    private String subSource;

    public static Model of(ModelRequest modelRequest) {
        return new Model()
                .setDialCode(modelRequest.getPhoneNumber().getCountryCode())
                .setPhoneNumber(modelRequest.getPhoneNumber().getNumber())
                .setEmail(modelRequest.getEmail().getAddress())
                .setSource(modelRequest.getSource())
                .setSubSource(modelRequest.getSubSource());
    }
}
