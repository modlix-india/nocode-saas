package com.fincity.saas.message.model.request.message.provider.whatsapp;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.Identity;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

/**
 * Queue-driven template send.
 *
 * <p>Unlike {@link TicketWhatsappTemplateMessageRequest}, which carries a fully built
 * {@code TemplateMessage}, this request names a stored template by id and supplies its placeholder
 * values. The message service resolves the template and assembles the components, so the publisher
 * (entity-processor) never needs to know a template's component structure.
 */
@Data
@Accessors(chain = true)
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class TicketWhatsappQueueTemplateRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 5512377419923318841L;

    private Identity ticketId;
    private ULong messageTemplateId;
    private Map<String, Object> variables;

    /**
     * Welcome-packet asset, supplied as the template's header media.
     *
     * <p>A link rather than an uploaded media id: Meta fetches it itself at send time, so there is
     * no upload round trip and no media-id expiry to manage. Null for an ordinary text template.
     */
    private String headerMediaUrl;

    /** {@code image}, {@code video} or {@code document}. Ignored when there is no header media. */
    private String headerMediaType;

    /** Body variable accompanying the asset. */
    private String caption;

    public boolean isValid() {
        return this.ticketId != null && !this.ticketId.isNull() && this.messageTemplateId != null;
    }
}
