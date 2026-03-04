package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.oserver.message.model.MessageTemplateQueObject;
import com.fincity.saas.entity.processor.oserver.message.model.WhatsappTemplateSendRequest;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class WhatsappTemplateQueueListener {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappTemplateQueueListener.class);

    private final IFeignMessageService feignMessageService;

    public WhatsappTemplateQueueListener(IFeignMessageService feignMessageService) {
        this.feignMessageService = feignMessageService;
    }

    @RabbitListener(
            queues = "#{'${entity.processor.whatsapp.mq.outbox:whatsapp.outbox}'}",
            containerFactory = "directMessageListener",
            messageConverter = "jsonMessageConverter")
    public void receive(
            @Payload MessageTemplateQueObject qob, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        if (qob.getXDebug() != null) MDC.put(LogUtil.DEBUG_KEY, qob.getXDebug());

        logger.info("{} - Received whatsapp template message: {}", qob.getXDebug(), qob);

        try {
            this.process(qob)
                    .doOnSuccess(v -> logger.info("{} - Sent whatsapp template message: {}", qob.getXDebug(), qob))
                    .doOnError(ex -> logger.error("Failed to send whatsapp template message : {}", qob, ex))
                    .block();
        } finally {
            if (qob.getXDebug() != null) {
                MDC.remove(LogUtil.DEBUG_KEY);
            }
        }
    }

    private Mono<Void> process(MessageTemplateQueObject qob) {

        WhatsappTemplateSendRequest request = new WhatsappTemplateSendRequest()
                .setTicketId(qob.getTicketId())
                .setMessageTemplateId(qob.getMessageTemplateId())
                .setVariables(qob.getVariables());

        return this.feignMessageService
                .sendWhatsappTemplateFromQueue(qob.getAppCode(), qob.getClientCode(), request)
                .onErrorResume(Mono::error);
    }
}
