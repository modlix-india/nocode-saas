package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.Template;
import com.fincity.saas.core.repository.TemplateRepository;
import com.fincity.saas.core.service.TemplateService;

@RestController
@RequestMapping("api/core/templates")
public class TemplateController extends AbstractOverridableDataController<Template, TemplateRepository, TemplateService> {

}
