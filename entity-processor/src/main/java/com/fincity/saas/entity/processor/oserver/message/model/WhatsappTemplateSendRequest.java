package com.fincity.saas.entity.processor.oserver.message.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WhatsappTemplateSendRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -112233445566778899L;

    private String ticketId;
    private Long messageTemplateId;
    private Map<String, Object> variables;
}
