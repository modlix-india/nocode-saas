package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.mongo.document.Function;
import com.fincity.saas.commons.mongo.repository.FunctionRepository;
import com.fincity.saas.commons.mongo.service.FunctionService;

@RestController
@RequestMapping("api/ui/functions")
public class FunctionController
        extends AbstractOverridableDataController<Function, FunctionRepository, FunctionService> {
}
