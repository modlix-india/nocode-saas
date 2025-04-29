package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.Email;
import com.fincity.saas.entity.processor.model.base.Identity;
import com.fincity.saas.entity.processor.model.base.PhoneNumber;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EntityRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3948634318723751023L;

    private String name;
    private String description;
    private Identity productId;
    private PhoneNumber phoneNumber;
    private Email email;
    private String source;
    private String subSource;
}
