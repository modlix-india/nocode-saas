package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappDebugTokenService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Meta token introspection, restricted to tenant owners.
 *
 * <p>Annotated on the controller rather than the service, which is the usual place in this
 * codebase, because these services are shared with the inbound webhook path. That path runs with no
 * user at all, so a class-level rule on the service would gate HTTP access and break message
 * delivery at the same time. The controller is the boundary that only humans cross.
 *
 * <p>This one deserves the tightest gate of the set: it builds a Meta app access token from
 * {@code client_id|client_secret} and returns Meta's introspection of it. It is a debugging aid
 * that has been reachable by any authenticated user in the tenant, and is a fair candidate for
 * deletion rather than protection.
 *
 * <p>Annotated per method, never on the class. A class-level rule applies to every public method
 * including the inherited {@code @InitBinder}, and reactive method security only supports methods
 * returning a {@code Publisher}, so a {@code void} binder callback makes every request to this
 * controller fail with a 500 before it is even routed. It fails for owners too, so it is not the
 * kind of mistake that shows up only in an access test.
 *
 * <p>What that leaves open is deliberate rather than incidental. The declared methods here are all
 * administrative writes and carry the gate; the generic read endpoints inherited from
 * {@code BaseUpdatableController} do not, because the deal profile calls them as an ordinary sales
 * agent to list approved templates and business numbers in order to send a message. Reading the
 * templates you are allowed to send is deal work, not settings administration. Those reads are
 * closed by moving them behind entity-processor and blocking {@code /api/message/**} at nginx, not
 * by an authority a salesperson will never hold.
 */
@RestController
@RequestMapping("/api/message/whatsapp/debug-token")
public class WhatsappTokenDebugController {

    private final WhatsappDebugTokenService whatsappDebugTokenService;

    @Autowired
    public WhatsappTokenDebugController(WhatsappDebugTokenService whatsappDebugTokenService) {
        this.whatsappDebugTokenService = whatsappDebugTokenService;
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @GetMapping("/{connectionName}")
    public Mono<ResponseEntity<Map<String, Object>>> sendWhatsappMessage(@PathVariable String connectionName) {
        return this.whatsappDebugTokenService.debugToken(connectionName).map(ResponseEntity::ok);
    }
}
