package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.CoreFiller;
import com.fincity.saas.core.repository.CoreFillerDocumentRepository;
import com.fincity.saas.core.service.CoreFillerService;

@RestController
@RequestMapping("api/core/filler")
public class CoreFillerController
                extends AbstractOverridableDataController<CoreFiller, CoreFillerDocumentRepository, CoreFillerService> {

}
