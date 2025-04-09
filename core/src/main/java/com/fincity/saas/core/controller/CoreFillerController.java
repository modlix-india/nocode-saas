package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.CoreFiller;
import com.fincity.saas.commons.core.repository.CoreFillerDocumentRepository;
import com.fincity.saas.commons.core.service.CoreFillerService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/filler")
public class CoreFillerController
                extends AbstractOverridableDataController<CoreFiller, CoreFillerDocumentRepository, CoreFillerService> {

}
