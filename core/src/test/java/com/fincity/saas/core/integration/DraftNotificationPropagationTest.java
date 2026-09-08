package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.service.NotificationService;
import com.fincity.saas.commons.mq.notifications.NotificationQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * A notification raised on the draft surface resolves DRAFT definitions.
 *
 * The notification pipeline crosses RabbitMQ exactly like the event pipeline, and
 * has the same problem: the sender runs on its own thread with no inbound request,
 * so an ambient flag cannot reach it. Without the marker on the message, a
 * notification raised from a draft page resolves the LIVE Notification and
 * Connection documents, so the draft surface silently previews the published
 * template rather than the one being worked on.
 *
 * Note what this deliberately does NOT change: recipients. The marker travels to
 * the two core lookups and nowhere else, so the user directory is still the real
 * one. That is the intended behaviour, not an oversight, and it is why the header
 * is set on those two Feign methods by hand rather than through the notification
 * module's RequestInterceptor, which would have applied it to every client
 * including the security one.
 */
@DisplayName("Draft marker across the notification queue")
class DraftNotificationPropagationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    private NotificationQueObject raiseAndCapture(boolean onDraftSurface) {

        Mockito.clearInvocations(this.amqpTemplate);

        // A system client short-circuits the target access check, which is not what
        // is under test here.
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Notification"));

        Mono<Boolean> raise = this.notificationService
                .processAndSendNotification(APP_CODE, SYSTEM, "appNotification", BigInteger.ONE, null, NotificationService.USER_ID,
                        null, "testNotification", "SYSTEM", Map.of("k", "v"))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));

        if (onDraftSurface)
            raise = raise.contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE));
        raise.block();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(this.amqpTemplate).convertAndSend(Mockito.anyString(), Mockito.anyString(), captor.capture());

        Object sent = captor.getValue();
        assertTrue(sent instanceof NotificationQueObject, "expected a NotificationQueObject on the wire, got " + sent);
        return (NotificationQueObject) sent;
    }

    @Test
    @Timeout(30)
    @DisplayName("a notification raised on the draft surface carries the marker")
    void draftNotificationCarriesMarker() {

        NotificationQueObject sent = raiseAndCapture(true);

        assertNotNull(sent);
        assertTrue(sent.isDraft(),
                "the marker was lost at publish time, so the sender would resolve the live definitions");
    }

    @Test
    @Timeout(30)
    @DisplayName("a notification raised on the live surface does not")
    void liveNotificationCarriesNoMarker() {

        NotificationQueObject sent = raiseAndCapture(false);

        assertNotNull(sent);
        assertFalse(sent.isDraft(), "a live notification must not be marked as draft");
    }

    @Test
    @Timeout(30)
    @DisplayName("the marker survives the queue's own JSON converter")
    void markerSurvivesTheRealConverter() {

        NotificationQueObject sent = raiseAndCapture(true);

        // Not a hand-rolled copy: this is the converter the broker actually uses
        // (IMQConfiguration wires Jackson2JsonMessageConverter), so a field Jackson
        // cannot see would fail here and nowhere else in the suite.
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        Message message = converter.toMessage(sent, new MessageProperties());
        Object back = converter.fromMessage(message);

        assertTrue(back instanceof NotificationQueObject, "converter did not round-trip the type");
        assertTrue(((NotificationQueObject) back).isDraft(), "the marker did not survive serialization");
        assertEquals(sent.getNotificationName(), ((NotificationQueObject) back).getNotificationName());
    }
}
