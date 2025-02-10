package com.fincity.saas.notification.service.template;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TemplateProcessor extends AbstractTemplateProcessor {

	@Autowired
	private TemplateService templateService;


}
