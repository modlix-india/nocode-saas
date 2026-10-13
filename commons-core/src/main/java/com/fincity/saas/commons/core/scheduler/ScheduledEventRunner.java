package com.fincity.saas.commons.core.scheduler;

import com.fincity.saas.commons.mq.events.EventQueObject;

import reactor.core.publisher.Mono;

/**
 * Runs an event's EventAction in this JVM, exactly as the events-queue listener
 * would, and answers whether its tasks succeeded. Core's EventsQueListener is the
 * implementation.
 *
 * <p>
 * The scheduler uses it to run the ticks and one-offs it claims itself instead
 * of publishing them: an instance claims a (client, tick) only while it has a
 * free run slot, so the work spreads over the instances by what each can take.
 * Where no runner exists, the scheduler publishes to the queue as before.
 */
public interface ScheduledEventRunner {

    Mono<Boolean> run(EventQueObject qob);
}
