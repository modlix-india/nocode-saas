package com.fincity.saas.entity.processor.dto.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks the wire shape of a WhatsApp message.
 *
 * <p>This DTO replaces one that lived in the message service, and the leadzump UI was written
 * against the old JSON. The field names here are therefore a contract with pages already in
 * production, not an implementation detail: a mismatch does not throw, it renders wrongly.
 *
 * <p>The specific trap is {@code isOutbound}. Declared as a primitive {@code boolean}, Lombok
 * generates {@code isOutbound()} and Jackson publishes {@code "outbound"}. Declared as {@code
 * Boolean}, it generates {@code getIsOutbound()} and publishes {@code "isOutbound"}. The UI binds
 * {@code Parent.outbound} in dozens of places to choose which side of the thread a bubble sits on,
 * so the wrapper type would have quietly rendered every message as inbound.
 */
class WhatsappMessageSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("direction publishes as 'outbound', which is what the UI binds")
    void publishesOutboundNotIsOutbound() {

        Map<String, Object> json = serialise(new WhatsappMessage().setOutbound(true));

        assertTrue(json.containsKey("outbound"), "UI binds Parent.outbound; renaming this flips every bubble");
        assertEquals(Boolean.TRUE, json.get("outbound"));
    }

    @Test
    @DisplayName("an inbound message publishes outbound=false rather than omitting the field")
    void publishesInboundExplicitly() {
        assertEquals(Boolean.FALSE, serialise(new WhatsappMessage().setOutbound(false)).get("outbound"));
    }

    @Test
    @DisplayName("keeps the field names the old message-service DTO published")
    void keepsInheritedFieldNames() {

        Map<String, Object> json = serialise(new WhatsappMessage()
                .setMessageId("wamid.TEST")
                .setCustomerWaId("919999999999")
                .setBodyText("hello"));

        // Every one of these is read by dealProfile's WhatsApp tab.
        //
        // bodyText is the message itself, and it is here because it was briefly not read at all:
        // both composers bound the retired Cloud API payload shape (message.text.body), which the
        // bridge does not fill, so every bubble rendered its fallback dash while the text sat
        // correctly in the database. That failure is silent by construction - a missing binding
        // shows a placeholder, not an error - so the wire name is worth pinning down.
        for (String field :
                new String[] {"messageId", "customerWaId", "messageType", "messageStatus", "outbound", "bodyText"})
            assertTrue(json.containsKey(field), "missing wire field: " + field);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> serialise(WhatsappMessage message) {
        return this.objectMapper.convertValue(message, Map.class);
    }
}
