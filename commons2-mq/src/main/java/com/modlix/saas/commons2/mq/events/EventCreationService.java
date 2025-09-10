package com.modlix.saas.commons2.mq.events;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.mq.util.LogUtil;
import com.modlix.saas.commons2.util.data.CircularLinkedList;
import com.modlix.saas.commons2.util.data.DoublePointerNode;

import jakarta.annotation.PostConstruct;

@Service
public class EventCreationService {

    private static final Logger logger = LogManager.getLogger(EventCreationService.class);

    @Value("${events.mq.exchange:events}")
    private String exchange;

    @Value("${events.mq.routingkeys:events1,events2,events3}")
    private String routingKey;

    @Autowired
    private AmqpTemplate amqpTemplate;

    private DoublePointerNode<String> nextRoutingKey;

    @PostConstruct
    protected void init() {
        nextRoutingKey = new CircularLinkedList<>(this.routingKey.split(",")).getHead();
    }

    public boolean createEvent(EventQueObject queObj) {
        this.nextRoutingKey = nextRoutingKey.getNext();

        try {
            amqpTemplate.convertAndSend(exchange, nextRoutingKey.getItem(), queObj);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send event to MQ", e);
            return false;
        }
    }
}
