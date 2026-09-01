package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The draft marker has to survive the message queue.
 *
 * A row written on the draft surface into a storage with generateEvents raises an
 * event. The event's CALL_CORE_FUNCTION action executes a KIRun function, and any
 * CoreServices.Storage write it performs resolves its database from
 * LogUtil.isDraft(). The listener runs on its own thread with no inbound request,
 * so if the marker is not on the message the function ran with isDraft() == false
 * and wrote to the LIVE database. The draft surface's data isolation was defeated
 * by its own event pipeline, and silently.
 *
 * The publish half is asserted here by capturing what actually goes onto the wire.
 * A full round trip needs a broker, which this harness deliberately does not have
 * (AmqpTemplate is mocked), so the consume half is asserted separately by checking
 * the flag is on the object the listener would receive.
 */
@DisplayName("Draft marker across the event queue")
class DraftEventPropagationTest extends AbstractIntegrationTest {

    @Autowired
    private EventCreationService eventCreationService;

    private EventQueObject publishAndCapture(boolean onDraftSurface) {

        Mockito.clearInvocations(this.amqpTemplate);

        EventQueObject obj = new EventQueObject()
                .setEventName("Storage.testStorage.Create")
                .setAppCode(APP_CODE)
                .setClientCode(SYSTEM);

        Mono<Boolean> publish = this.eventCreationService.createEvent(obj);
        if (onDraftSurface)
            publish = publish.contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE));
        publish.block();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(this.amqpTemplate).convertAndSend(Mockito.anyString(), Mockito.anyString(),
                captor.capture());

        Object sent = captor.getValue();
        assertTrue(sent instanceof EventQueObject, "expected an EventQueObject on the wire, got " + sent);
        return (EventQueObject) sent;
    }

    @Test
    @Timeout(30)
    @DisplayName("an event raised on the draft surface carries the marker")
    void draftEventCarriesMarker() {

        EventQueObject sent = publishAndCapture(true);

        assertNotNull(sent);
        assertTrue(sent.isDraft(),
                "the marker was lost at publish time, so the consumer would write to the live database");
    }

    @Test
    @Timeout(30)
    @DisplayName("an event raised on the live surface does not")
    void liveEventCarriesNoMarker() {

        EventQueObject sent = publishAndCapture(false);

        assertNotNull(sent);
        assertFalse(sent.isDraft(), "a live event must not be marked as draft");
    }

    @Test
    @Timeout(30)
    @DisplayName("the marker survives the queue's own JSON converter")
    void markerSurvivesSerialization() {

        EventQueObject sent = publishAndCapture(true);

        // The converter the broker actually uses, not a hand-rolled copy:
        // IMQConfiguration wires Jackson2JsonMessageConverter. A field Jackson
        // cannot see would drop silently on the wire and reintroduce the bug,
        // and this is the only place in the suite that would catch it.
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        Message message = converter.toMessage(sent, new MessageProperties());
        Object back = converter.fromMessage(message);

        assertTrue(back instanceof EventQueObject, "converter did not round-trip the type");
        assertTrue(((EventQueObject) back).isDraft(), "the marker did not survive serialization");
        assertEquals(sent.getEventName(), ((EventQueObject) back).getEventName());
    }
}
