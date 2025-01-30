package com.fincity.saas.ui.service;

import com.fincity.saas.commons.mongo.service.AbstractDeletionService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeletionService extends AbstractDeletionService {

    private final List<AbstractOverridableDataService<?, ?>> serviceList;

    public DeletionService(IFeignSecurityService feignSecurityService,
                           ApplicationService appService,
                           PageService pageService,
                           StyleService styleService,
                           StyleThemeService themeService,
                           UIFunctionService funService,
                           UISchemaService schemaService,
                           UIFillerService fillerService,
                           URIPathService uriPathService,
                           TransportService transportService) {
        super(feignSecurityService);
        serviceList = List.of(
            appService,
            pageService,
            styleService,
            themeService,
            funService,
            schemaService,
            fillerService,
            uriPathService,
            transportService);
    }

    @Override
    public List<AbstractOverridableDataService<?, ?>> getServices() {
        return this.serviceList;
    }
}
