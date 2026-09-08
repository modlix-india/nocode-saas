package com.fincity.saas.ui.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.service.AbstractPublishService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;

/**
 * The ui module's publishable object list. Everything else is in
 * AbstractPublishService, shared with core.
 */
@Service
public class PublishService extends AbstractPublishService {

    public PublishService(DraftService draftService, FeignAuthenticationService securityService,
            AbstractMongoMessageResourceService messageResourceService, ApplicationService applicationService,
            PageService pageService, StyleService styleService, StyleThemeService styleThemeService,
            UIFunctionService functionService, UISchemaService schemaService, URIPathService uriPathService,
            UIFillerService fillerService) {

        // UIFiller is included deliberately: it is in the transport list and is easy
        // to forget. Personalization is excluded, being per-user runtime state that
        // is not versionable and has no publish story.
        super(draftService, securityService, messageResourceService,
                List.<AbstractOverridableDataService<?, ?>>of(applicationService, pageService, styleService,
                        styleThemeService, functionService, schemaService, uriPathService, fillerService));
    }
}
