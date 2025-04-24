package com.fincity.saas.entity.processor.model;

import com.fincity.saas.entity.processor.model.base.Email;
import com.fincity.saas.entity.processor.model.base.PhoneNumber;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ModelRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 8432447203359141912L;

    private String name;
    private String description;
    private PhoneNumber phoneNumber;
    private Email email;

    public static ModelRequest of(EntityRequest entityRequest) {
        return new ModelRequest()
                .setName(entityRequest.getName())
                .setDescription(entityRequest.getDescription())
                .setPhoneNumber(entityRequest.getPhoneNumber())
                .setEmail(entityRequest.getEmail());
    }
}
