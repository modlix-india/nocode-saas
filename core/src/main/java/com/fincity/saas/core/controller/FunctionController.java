package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.CoreFunction;
import com.fincity.saas.core.repository.CoreFunctionDocumentRepository;
import com.fincity.saas.core.service.CoreFunctionService;

@RestController
@RequestMapping("api/core/functions")
public class FunctionController
        extends AbstractOverridableDataController<CoreFunction, CoreFunctionDocumentRepository, CoreFunctionService> {
}
