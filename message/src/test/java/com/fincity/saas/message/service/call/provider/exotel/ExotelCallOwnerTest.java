package com.fincity.saas.message.service.call.provider.exotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelConnectAppletRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.bridge.BridgeSessionService;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * Who owns a call and a WhatsApp number, so any app can take either over without a code change.
 *
 * <p>An inbound call used to be stamped with the global {@code message.call.default-owner-service}
 * whatever account it arrived on. It now takes its owner from the call account (the Exotel
 * connection of the app and client it arrived for), then from the service answering the connect
 * applet, and has none (its events park) when neither says.
 */
class ExotelCallOwnerTest {

    private static final MessageAccess ACCESS = MessageAccess.of("leadzumptwo", "FIN", Boolean.TRUE);

    private static Connection account(Object owner) {
        Map<String, Object> details = new HashMap<>(Map.of("accountSid", "acc1", "callerId", "08047000000"));
        if (owner != null) details.put(ExotelCallService.CONNECTION_OWNER_SERVICE, owner);
        Connection c = new Connection().setConnectionDetails(details);
        c.setName("exotel_connection");
        return c;
    }

    private static IncomingCallRequest answeredBy(String owner) {
        return new IncomingCallRequest().setOwnerService(owner);
    }

    private static ExotelConnectAppletRequest call() {
        ExotelConnectAppletRequest r = new ExotelConnectAppletRequest();
        r.setCallSid("sid-1");
        r.setTo("08047000000");
        return r;
    }

    private final ExotelCallService service = new ExotelCallService();

    @Test
    void theCallAccountNamesTheOwner() {
        assertEquals("core", this.service.inboundOwner(ACCESS, account("core"), answeredBy(null), call()));
        assertEquals("core", this.service.inboundOwner(ACCESS, account(" core "), answeredBy(""), call()));
    }

    @Test
    void theAccountWinsOverTheAnsweringService() {
        assertEquals("core",
                this.service.inboundOwner(ACCESS, account("core"), answeredBy("entity-processor"), call()));
    }

    @Test
    void anAccountWithoutAnOwnerFallsBackToTheAnsweringService() {
        assertEquals("entity-processor",
                this.service.inboundOwner(ACCESS, account(null), answeredBy("entity-processor"), call()));
        assertEquals("entity-processor",
                this.service.inboundOwner(ACCESS, account("  "), answeredBy("entity-processor"), call()));
    }

    @Test
    void withNeitherTheCallHasNoOwnerAndParks() {
        assertNull(this.service.inboundOwner(ACCESS, account(null), answeredBy(null), call()));
        assertNull(this.service.inboundOwner(ACCESS, null, answeredBy(" "), call()));
    }

    @Test
    void accountOwnerReadsOnlyTheConnection() {
        assertNull(ExotelCallService.accountOwner(null));
        assertNull(ExotelCallService.accountOwner(new Connection()));
        assertEquals("core", ExotelCallService.accountOwner(account("core")));
    }

    @Test
    void aNumberLinkedWithoutAnOwnerBelongsToCore() throws Exception {
        Field f = BridgeSessionService.class.getDeclaredField("defaultOwnerService");
        String expression = f.getAnnotation(Value.class).value();
        assertEquals("${message.bridge.default-owner-service:core}", expression);
        assertTrue(expression.endsWith(":core}"));
    }
}
