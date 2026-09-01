package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractPublishController;
import com.fincity.saas.ui.service.PublishService;

@RestController
@RequestMapping("api/ui/publish")
public class PublishController extends AbstractPublishController<PublishService> {

    public PublishController(PublishService publishService) {
        super(publishService);
    }
}
