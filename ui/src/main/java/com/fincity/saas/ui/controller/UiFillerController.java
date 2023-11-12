package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.UIFiller;
import com.fincity.saas.ui.repository.UIFillerDocumentRepository;
import com.fincity.saas.ui.service.UIFillerService;

@RestController
@RequestMapping("api/ui/filler")
public class UiFillerController
        extends AbstractOverridableDataController<UIFiller, UIFillerDocumentRepository, UIFillerService> {

}
