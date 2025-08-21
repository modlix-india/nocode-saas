package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappDebugTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

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
