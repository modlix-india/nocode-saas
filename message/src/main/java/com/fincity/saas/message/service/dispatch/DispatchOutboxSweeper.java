package com.fincity.saas.message.service.dispatch;

import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.dispatch.DispatchOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Retries handoffs the first attempt could not deliver, across every channel.
 *
 * <p>Without this the outbox is decoration: rows would accumulate and nothing would ever send them.
 * It is what turns "entity-processor was down for ten minutes" from lost messages into a delay.
 *
 * <p>Rows past the attempt ceiling are deliberately left alone rather than retried or deleted. A
 * permanently undeliverable message is something a person needs to see, and both spinning on it and
 * quietly discarding it hide that.
 */
@Component
public class DispatchOutboxSweeper {

    private static final Logger logger = LoggerFactory.getLogger(DispatchOutboxSweeper.class);

    private final DispatchOutboxService outboxService;
    private final EventDispatcher dispatcher;

    @Value("${message.dispatch.outbox.sweep-batch-size:50}")
    private int batchSize;

    public DispatchOutboxSweeper(DispatchOutboxService outboxService, EventDispatcher dispatcher) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
    }

    @Scheduled(
            initialDelayString = "${message.dispatch.outbox.sweep-initial-delay-ms:60000}",
            fixedDelayString = "${message.dispatch.outbox.sweep-interval-ms:60000}")
    public void sweep() {
        this.outboxService
                .readDue(this.batchSize)
                .flatMapMany(Flux::fromIterable)
                // One at a time. These are retries of things that already failed, so there is no
                // hurry, and a burst of parallel calls at a service that is still recovering is
                // exactly the wrong thing to do.
                .concatMap(row -> this.dispatcher
                        .deliver(MessageAccess.of(row.getAppCode(), row.getClientCode(), Boolean.TRUE), row)
                        .onErrorResume(e -> {
                            logger.error("Sweeper could not process outbox row {}.", row.getId(), e);
                            return Mono.empty();
                        }))
                .then(this.reportExhausted())
                .subscribe(
                        null,
                        e -> logger.error("Dispatch outbox sweep failed.", e));
    }

    private Mono<Void> reportExhausted() {
        return this.outboxService
                .countExhausted()
                .doOnNext(count -> {
                    if (count > 0)
                        logger.error(
                                "{} handoff(s) have exhausted their retries and are stuck in the outbox."
                                        + " These messages have not reached the owning service and will not be"
                                        + " retried automatically.",
                                count);
                })
                .then();
    }
}
