package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.UIFunction;
import com.fincity.saas.ui.repository.UIFunctionDocumentRepository;
import com.fincity.saas.ui.service.UIFunctionService;

@RestController
@RequestMapping("api/ui/functions")
public class FunctionController
        extends AbstractOverridableDataController<UIFunction, UIFunctionDocumentRepository, UIFunctionService> {
}
