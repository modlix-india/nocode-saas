package com.fincity.saas.commons.core.service.connection.email;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Template;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface IAppEmailService {
    Mono<Boolean> sendMail(
            List<String> toAddresses, Template template, Map<String, Object> templateData, Connection connection);
}
