package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.URI;
import com.fincity.saas.ui.repository.URIRepository;
import com.fincity.saas.ui.service.URIService;

@RestController
@RequestMapping("api/ui/uris")
public class URIController extends AbstractOverridableDataController<URI, URIRepository, URIService> {

}
