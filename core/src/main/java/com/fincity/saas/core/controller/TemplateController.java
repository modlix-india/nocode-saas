package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.repository.TemplateRepository;
import com.fincity.saas.commons.core.service.TemplateService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/templates")
public class TemplateController extends AbstractOverridableDataController<Template, TemplateRepository, TemplateService> {

}
