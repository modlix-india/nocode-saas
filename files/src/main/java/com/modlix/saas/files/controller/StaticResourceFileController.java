package com.modlix.saas.files.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.files.service.StaticFileResourceService;

@RestController
@RequestMapping("api/files/static")
public class StaticResourceFileController extends AbstractResourceFileController<StaticFileResourceService> {

    public StaticResourceFileController(StaticFileResourceService service) {
        super(service);
    }
}
