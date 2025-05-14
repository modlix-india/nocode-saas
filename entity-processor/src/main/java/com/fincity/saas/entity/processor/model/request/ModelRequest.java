package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ModelRequest extends BaseRequest<ModelRequest> {

    @Serial
    private static final long serialVersionUID = 8432447203359141912L;

    private PhoneNumber phoneNumber;
    private Email email;
    private String source;
    private String subSource;

    public static ModelRequest of(EntityRequest entityRequest) {
        return new ModelRequest()
                .setName(entityRequest.getName())
                .setDescription(entityRequest.getDescription())
                .setPhoneNumber(entityRequest.getPhoneNumber())
                .setEmail(entityRequest.getEmail())
                .setSource(entityRequest.getSource())
                .setSubSource(entityRequest.getSubSource());
    }
}
