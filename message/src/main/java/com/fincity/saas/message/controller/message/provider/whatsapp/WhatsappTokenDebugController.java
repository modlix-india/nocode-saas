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
 */
@PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
@RestController
@RequestMapping("/api/message/whatsapp/debug-token")
public class WhatsappTokenDebugController {

    private final WhatsappDebugTokenService whatsappDebugTokenService;

    @Autowired
    public WhatsappTokenDebugController(WhatsappDebugTokenService whatsappDebugTokenService) {
        this.whatsappDebugTokenService = whatsappDebugTokenService;
    }

    @GetMapping("/{connectionName}")
    public Mono<ResponseEntity<Map<String, Object>>> sendWhatsappMessage(@PathVariable String connectionName) {
        return this.whatsappDebugTokenService.debugToken(connectionName).map(ResponseEntity::ok);
    }
}
