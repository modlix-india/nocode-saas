package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumberRecord;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappPhoneNumberService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp/phone-numbers")
public class WhatsappPhoneNumberController
        extends BaseUpdatableController<
                MessageWhatsappPhoneNumberRecord,
                WhatsappPhoneNumber,
                WhatsappPhoneNumberDAO,
                WhatsappPhoneNumberService> {

    @PostMapping("/sync/{connectionName}")
    public Mono<ResponseEntity<List<WhatsappPhoneNumber>>> syncPhoneNumbers(@PathVariable String connectionName) {
        return this.service.syncPhoneNumbers(connectionName).collectList().map(ResponseEntity::ok);
    }
}
