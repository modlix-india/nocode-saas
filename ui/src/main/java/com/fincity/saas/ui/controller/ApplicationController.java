package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;
import com.fincity.saas.ui.service.ApplicationService;

@RestController
@RequestMapping("api/ui/applications")
public class ApplicationController
        extends AbstractUIController<Application, ApplicationRepository, ApplicationService> {

}
