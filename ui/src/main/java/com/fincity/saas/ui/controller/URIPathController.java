package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.URIPath;
import com.fincity.saas.ui.repository.URIPathRepository;
import com.fincity.saas.ui.service.URIPathService;

@RestController
@RequestMapping("api/ui/uripaths")
public class URIPathController extends AbstractOverridableDataController<URIPath, URIPathRepository, URIPathService> {

}
