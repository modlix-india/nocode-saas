package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.phone.type.CodeMethodType;
import com.fincity.saas.message.model.message.whatsapp.templates.type.LanguageType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class RequestCode implements Serializable {

    @Serial
    private static final long serialVersionUID = -6533170228528837137L;

    @JsonProperty("code_method")
    private CodeMethodType codeMethod;

    @JsonProperty("language")
    private LanguageType language;
}
