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

    private String connectionName;
    private String ticketId;
    private Long messageTemplateId;
    private Map<String, Object> variables;

    /**
     * Welcome-packet asset, sent as the template's header media.
     *
     * <p>A link, not an uploaded media id: Meta fetches it at send time, so there is no upload step
     * and no media-id expiry. Null for an ordinary text template.
     */
    private String headerMediaUrl;

    /** {@code image}, {@code video} or {@code document}. */
    private String headerMediaType;

    /** Body variable accompanying the asset, exposed to the template as {{caption}}. */
    private String caption;
}
