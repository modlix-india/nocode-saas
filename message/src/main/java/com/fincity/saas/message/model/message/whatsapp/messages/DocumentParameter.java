package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentParameter extends Parameter {

    @Serial
    private static final long serialVersionUID = -6116517331733443133L;

    private Document document;

    public DocumentParameter() {
        super(ParameterType.DOCUMENT);
    }

    public DocumentParameter(Document document) {
        super(ParameterType.DOCUMENT);
        this.document = document;
    }
}
