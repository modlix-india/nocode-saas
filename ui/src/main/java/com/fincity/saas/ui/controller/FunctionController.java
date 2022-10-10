package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Function;
import com.fincity.saas.ui.repository.FunctionRepository;
import com.fincity.saas.ui.service.FunctionService;

@RestController
@RequestMapping("api/ui/functions")
public class FunctionController
        extends AbstractUIController<Function, FunctionRepository, FunctionService> {
}
