package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * A number linked through entity-processor is entity-processor's.
 *
 * <p>The message service's default owner for a session that names none is now {@code core}, so any
 * app can own a number. entity-processor must therefore name itself, or a number linked from
 * leadzump's integration page would deliver its messages to core instead of here.
 */
class WhatsappSessionOwnerTest {

    private final List<Map<String, Object>> bodies = new ArrayList<>();

    private WhatsappSendOptionsService service() {
        IFeignMessageService feign = (IFeignMessageService) Proxy.newProxyInstance(
                IFeignMessageService.class.getClassLoader(), new Class<?>[] {IFeignMessageService.class},
                (p, m, a) -> {
                    if (!"createWhatsappSession".equals(m.getName()))
                        throw new UnsupportedOperationException(m.getName());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) a[2];
                    this.bodies.add(body);
                    return Mono.just(Map.of("code", "SESS1"));
                });

        return new WhatsappSendOptionsService(feign, null, null, null, null) {
            @Override
            public Mono<ProcessorAccess> hasAccess() {
                return Mono.just(ProcessorAccess.of("leadzump", "FIN", true, null, null));
            }
        };
    }

    @Test
    void aSessionLinkedHereNamesEntityProcessorAsItsOwner() {
        assertEquals("SESS1", this.service()
                .createSession(new HashMap<>(Map.of("phoneNumber", "+919876543210")))
                .block()
                .get("code"));

        assertEquals("entity-processor", this.bodies.get(0).get("ownerService"));
        assertEquals("+919876543210", this.bodies.get(0).get("phoneNumber"));
    }

    @Test
    void aPageCannotHandTheNumberToAnotherOwnerThroughHere() {
        this.service().createSession(Map.of("phoneNumber", "+91987", "ownerService", "core")).block();

        assertEquals("entity-processor", this.bodies.get(0).get("ownerService"));
    }
}
