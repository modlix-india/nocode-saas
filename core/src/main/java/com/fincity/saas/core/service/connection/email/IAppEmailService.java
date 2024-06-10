package com.fincity.saas.core.service.connection.email;

import java.util.List;
import java.util.Map;

import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Template;

import reactor.core.publisher.Mono;

public interface IAppEmailService {

	Mono<Boolean> sendMail(List<String> toAddresses, Template template, Map<String, Object> templateData, Connection connection);
}
