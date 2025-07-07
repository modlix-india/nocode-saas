package com.fincity.saas.commons.core.service;

import com.fincity.saas.commons.core.document.CoreFiller;
import com.fincity.saas.commons.core.repository.CoreFillerDocumentRepository;
import com.fincity.saas.commons.mongo.service.AbstractFillerService;
import org.springframework.stereotype.Service;

@Service
public class CoreFillerService extends AbstractFillerService<CoreFiller, CoreFillerDocumentRepository> {

    protected CoreFillerService() {
        super(CoreFiller.class);
    }

    @Override
    public String getObjectName() {
        return "Filler";
    }

    @Override
    public String getAccessCheckName() {
        return "Application";
    }
}
