package com.fincity.saas.commons.core.mq.services;

import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.core.service.connection.email.EmailService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.StringUtil;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class EventEmailService implements IEventActionService {

    private EmailService emailService;

    @Autowired
    private void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject) {

        Map<String, Object> data = CommonsUtil.nonNullValue(queObject.getData(), Map.of());

        Map<String, Object> taskParameter = CommonsUtil.nonNullValue(task.getParameters(), Map.of());

        String templateName = StringUtil.safeValueOf(taskParameter.get("template"));
        String connectionName = StringUtil.safeValueOf(taskParameter.get("connectionName"));

        return emailService.sendEmail(
                queObject.getAppCode(), queObject.getClientCode(), null, templateName, connectionName, data);
    }
}
