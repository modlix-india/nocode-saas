package com.fincity.saas.entity.processor.service;

import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.model.common.Identity;

import reactor.core.publisher.Mono;

public interface IValueTemplateService{

	Mono<ValueTemplate> updateValueTemplate(Identity identity, ValueTemplate valueTemplate);
}
