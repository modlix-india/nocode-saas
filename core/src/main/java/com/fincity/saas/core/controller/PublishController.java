package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.core.service.CorePublishService;
import com.fincity.saas.commons.mongo.controller.AbstractPublishController;

@RestController
@RequestMapping("api/core/publish")
public class PublishController extends AbstractPublishController<CorePublishService> {

    public PublishController(CorePublishService publishService) {
        super(publishService);
    }
}
