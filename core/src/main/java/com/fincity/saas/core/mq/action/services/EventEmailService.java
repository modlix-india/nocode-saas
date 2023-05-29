package com.fincity.saas.core.mq.action.services;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.EventAction;
import com.fincity.saas.core.model.EventActionTask;
import com.fincity.saas.core.service.connection.email.EmailService;

import reactor.core.publisher.Mono;

@Service
public class EventEmailService implements IEventActionService {

	@Autowired
	private EmailService emailService;

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject) {

		Map<String, Object> data = CommonsUtil.nonNullValue(queObject.getData(), Map.of());

		Map<String, Object> taskParameter = CommonsUtil.nonNullValue(task.getParameters(), Map.of());

		String templateName = StringUtil.safeValueOf(taskParameter.get("template"));
		String connectionName = StringUtil.safeValueOf(taskParameter.get("connectionName"));

		return emailService.sendEmail(queObject.getAppCode(), queObject.getClientCode(), null, templateName, connectionName, data);
	}

}
