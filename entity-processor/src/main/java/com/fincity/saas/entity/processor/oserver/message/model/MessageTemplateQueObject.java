package com.fincity.saas.entity.processor.oserver.message.model;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MessageTemplateQueObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 8256941067844694617L;

    private String eventName;
    private String clientCode;
    private String appCode;
    private String xDebug;

    private String ticketId;
    private String productId;
    private String stageId;
    private String statusId;

    private String channel;
    private Long messageTemplateId;

    /** Core connection the message service resolves the WABA credentials from. */
    private String connectionName;

    private Map<String, Object> variables; // NOSONAR

    /**
     * Welcome-packet asset, sent as the template's header media.
     *
     * <p>Carried as a URL rather than an uploaded media id on purpose: Meta fetches the link itself
     * at send time, so there is no upload step, no media-id expiry and no per-phone-number media
     * cache to keep. The URL must therefore be reachable by Meta's servers without a session, and
     * the key on it must outlive the send plus the retry window.
     *
     * <p>Null for an ordinary text template, which is every config that predates the welcome pack.
     */
    private String headerMediaUrl;

    /** {@code image}, {@code video} or {@code document} - the Graph header parameter type. */
    private String headerMediaType;

    /** Body variable accompanying the asset. */
    private String caption;

    private ContextAuthentication authentication;
}
