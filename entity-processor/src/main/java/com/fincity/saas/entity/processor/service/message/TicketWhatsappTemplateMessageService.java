package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.dto.product.ProductMessageConfig;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.oserver.message.model.MessageTemplateQueObject;
import com.fincity.saas.entity.processor.service.ActivityService;
import com.fincity.saas.entity.processor.service.product.ProductCommService;
import java.util.HashMap;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketWhatsappTemplateMessageService implements TicketChannelMessageService {

    private final ActivityService activityService;
    private final TemplateEventPublisher templateEventPublisher;
    private final ProductCommService productCommService;

    @Value("${entity-processor.whatsapp.mq.holding.count:5}")
    private int holdingQueueCount;

    /**
     * Last-resort connection name, used only when the product has no WhatsApp {@code ProductComm}
     * and no app-level default exists. Connection names are tenant-authored, so this is a
     * compatibility fallback rather than a real default.
     */
    @Value("${entity-processor.whatsapp.fallback-connection-name:whatsapp_connection}")
    private String fallbackConnectionName;

    public TicketWhatsappTemplateMessageService(
            ActivityService activityService,
            TemplateEventPublisher templateEventPublisher,
            @Lazy ProductCommService productCommService) {
        this.activityService = activityService;
        this.templateEventPublisher = templateEventPublisher;
        this.productCommService = productCommService;
    }

    @Override
    public MessageChannelType getChannel() {
        return MessageChannelType.WHATS_APP_TEMPLATE;
    }

    @Override
    public Mono<Void> sendOnTicketCreate(ProcessorAccess access, Ticket ticket, ProductMessageConfig config) {

        return this.resolveConnectionName(access, ticket)
                .flatMap(connectionName -> this.templateEventPublisher.publish(
                        this.toQueObject(access, ticket, config, connectionName), this.resolveSlotIndex(config)))
                .then(this.activityService.acWhatsapp(ticket.getId(), null, ticket.getName()))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketWhatsappTemplateMessageService.sendOnTicketCreate"));
    }

    /**
     * Resolves which core {@code Connection} to send on. {@link ProductCommService#getProductComm}
     * already walks product-and-source, then product default, then app default, which is the same
     * per-product-with-fallback shape the WhatsApp phone number itself uses.
     */
    private Mono<String> resolveConnectionName(ProcessorAccess access, Ticket ticket) {

        return this.productCommService
                .getProductComm(
                        access,
                        ticket.getProductId(),
                        ConnectionType.TEXT,
                        ConnectionSubType.WHATSAPP,
                        ticket.getSource(),
                        ticket.getSubSource())
                .map(ProductComm::getConnectionName)
                .filter(name -> name != null && !name.isBlank())
                .defaultIfEmpty(this.fallbackConnectionName);
    }

    /**
     * Maps the config's order onto a holding queue. Orders beyond the configured queue count tail
     * into the last slot rather than routing to a queue that was never declared, which would drop
     * the message silently.
     */
    private int resolveSlotIndex(ProductMessageConfig cfg) {

        Integer order = cfg.getOrder();

        if (order == null || order < 0) return 0;

        return Math.min(order, this.holdingQueueCount - 1);
    }

    /**
     * Placeholder values available to the template body, keyed by name.
     *
     * <p>Note that Meta's default {@code POSITIONAL} templates use {@code {{1}}}, {@code {{2}}}
     * tokens, which will not match these names and will render blank. Supporting those needs a
     * per-config variable mapping, which {@link ProductMessageConfig} does not carry yet.
     */
    private Map<String, Object> buildVariables(ProcessorAccess access, Ticket ticket) {

        Map<String, Object> variables = new HashMap<>();

        variables.put("name", ticket.getName());
        variables.put("email", ticket.getEmail());
        variables.put("phoneNumber", ticket.getPhoneNumber());
        variables.put("ticketCode", ticket.getCode());
        variables.put("userName", access.getUserName());

        return variables;
    }

    private MessageTemplateQueObject toQueObject(
            ProcessorAccess access, Ticket ticket, ProductMessageConfig cfg, String connectionName) {

        ULong ticketId = ticket.getId();
        ULong productId = ticket.getProductId();
        ULong stageId = ticket.getStage();
        ULong statusId = ticket.getStatus();

        MessageTemplateQueObject que = new MessageTemplateQueObject()
                .setEventName("TicketCreated")
                .setAppCode(access.getAppCode())
                .setClientCode(access.getClientCode())
                .setConnectionName(connectionName)
                .setTicketId(ticketId != null ? ticketId.toString() : null)
                .setProductId(productId != null ? productId.toString() : null)
                .setStageId(stageId != null ? stageId.toString() : null)
                .setStatusId(statusId != null ? statusId.toString() : null)
                .setChannel(cfg.getChannel() != null ? cfg.getChannel().getLiteral() : null)
                .setVariables(this.buildVariables(access, ticket))
                .setMessageTemplateId(
                        cfg.getMessageTemplateId() != null
                                ? cfg.getMessageTemplateId().toBigInteger().longValue()
                                : null);

        FileDetail asset = cfg.getAssetFileDetail();
        if (asset != null && asset.getUrl() != null)
            que.setHeaderMediaUrl(asset.getUrl())
                    .setHeaderMediaType(headerMediaType(asset))
                    .setCaption(cfg.getCaption());

        return que;
    }

    /**
     * Maps the stored file onto the three header types Graph accepts.
     *
     * <p>Defaults to {@code document} rather than failing, because Graph rejects an unknown header
     * type outright and a brochure arriving as a document is a far better outcome than a welcome
     * packet that silently stops at the first unrecognised MIME type.
     */
    private static String headerMediaType(FileDetail asset) {

        String mime = asset.getType() == null ? "" : asset.getType().toLowerCase();

        if (mime.startsWith("image/")) return "image";
        if (mime.startsWith("video/")) return "video";

        return "document";
    }
}
