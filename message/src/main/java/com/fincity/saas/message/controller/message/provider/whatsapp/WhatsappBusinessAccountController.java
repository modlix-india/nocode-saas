package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappBusinessAccountDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappBusinessAccountsRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappBusinessAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp/accounts/business")
public class WhatsappBusinessAccountController
        extends BaseUpdatableController<
                MessageWhatsappBusinessAccountsRecord,
                WhatsappBusinessAccount,
                WhatsappBusinessAccountDAO,
                WhatsappBusinessAccountService> {

    @PostMapping("/sync")
    public Mono<ResponseEntity<WhatsappBusinessAccount>> syncBusinessAccount(
            @RequestParam final String connectionName) {
        return this.service.syncBusinessAccount(connectionName).map(ResponseEntity::ok);
    }

    @PostMapping("/webhook/override/{id}")
    public Mono<ResponseEntity<WhatsappBusinessAccount>> overrideWebhook(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity, @RequestParam final String connectionName) {
        return this.service.overrideWebhook(connectionName, identity).map(ResponseEntity::ok);
    }

    @GetMapping("/fb" + PATH_ID)
    public Mono<ResponseEntity<WhatsappBusinessAccount>> getBusinessAccount(@PathVariable final String id) {
        return this.service.getBusinessAccount(id).map(ResponseEntity::ok);
    }
}
