package com.fincity.saas.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.core.controller.CoreInternalEventController.RaiseRequest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CoreInternalEventControllerTest {

    private EventCreationService ecService;
    private CoreInternalEventController controller;

    @BeforeEach
    void setUp() {
        this.ecService = mock(EventCreationService.class);
        when(this.ecService.createEvent(any())).thenReturn(Mono.just(true));
        this.controller = new CoreInternalEventController(this.ecService);
    }

    @Test
    void raisesTheEventWithoutAuthenticationSoItRunsAsTheEventRunner() {

        Map<String, Object> data = Map.of("resourceType", "SECURED", "paths",
                List.of("/leadzumptwo/whatsapp/incoming/919/abc.jpg"));

        StepVerifier.create(this.controller.raise(new RaiseRequest("FILES_EXPIRED", "leadzumptwo", "FIN", data)))
                .assertNext(re -> {
                    assertEquals(HttpStatus.OK, re.getStatusCode());
                    assertEquals(Boolean.TRUE, re.getBody());
                })
                .verifyComplete();

        ArgumentCaptor<EventQueObject> captor = ArgumentCaptor.forClass(EventQueObject.class);
        verify(this.ecService).createEvent(captor.capture());
        EventQueObject qob = captor.getValue();
        assertEquals("FILES_EXPIRED", qob.getEventName());
        assertEquals("leadzumptwo", qob.getAppCode());
        assertEquals("FIN", qob.getClientCode());
        assertEquals(data, qob.getData());
        // No authentication: EventsQueListener builds the client's anonymous event-runner context.
        assertNull(qob.getAuthentication());
    }

    @Test
    void missingDataIsAnEmptyMap() {

        StepVerifier.create(this.controller.raise(new RaiseRequest("FILES_EXPIRED", "leadzumptwo", "FIN", null)))
                .assertNext(re -> assertEquals(Boolean.TRUE, re.getBody()))
                .verifyComplete();

        ArgumentCaptor<EventQueObject> captor = ArgumentCaptor.forClass(EventQueObject.class);
        verify(this.ecService).createEvent(captor.capture());
        assertTrue(captor.getValue().getData().isEmpty());
    }

    @Test
    void refusesAnEventThatIsNotRaisableHere() {

        StepVerifier.create(this.controller.raise(new RaiseRequest("WHATSAPP_INBOUND", "leadzumptwo", "FIN", Map.of())))
                .assertNext(re -> {
                    assertEquals(HttpStatus.BAD_REQUEST, re.getStatusCode());
                    assertEquals(Boolean.FALSE, re.getBody());
                })
                .verifyComplete();

        verify(this.ecService, never()).createEvent(any());
    }

    @Test
    void refusesMissingFields() {

        for (RaiseRequest r : List.of(new RaiseRequest(null, "leadzumptwo", "FIN", null),
                new RaiseRequest("FILES_EXPIRED", " ", "FIN", null),
                new RaiseRequest("FILES_EXPIRED", "leadzumptwo", null, null))) {
            StepVerifier.create(this.controller.raise(r))
                    .assertNext(re -> assertEquals(HttpStatus.BAD_REQUEST, re.getStatusCode()))
                    .verifyComplete();
        }

        StepVerifier.create(this.controller.raise(null))
                .assertNext(re -> assertEquals(HttpStatus.BAD_REQUEST, re.getStatusCode()))
                .verifyComplete();

        verify(this.ecService, never()).createEvent(any());
    }
}
