package com.fincity.saas.commons.core.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractDraftService;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.service.AbstractPublishService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;

/**
 * The core module's publishable object list. Everything else is in
 * AbstractPublishService, shared with ui.
 *
 * Every overridable core service is here. Leaving one out would not fail
 * anything visibly: its drafts would simply never appear in `pending` and never
 * be shipped by `publishAll`, so they would sit unpublished with nothing saying
 * why. That is the failure mode this list has to be checked against, and there is
 * a test that walks the Spring context to make sure it stays complete.
 */
@Service
public class CorePublishService extends AbstractPublishService {

    public CorePublishService(AbstractDraftService draftService, FeignAuthenticationService securityService,
            AbstractMongoMessageResourceService messageResourceService, StorageService storageService,
            ConnectionService connectionService, TemplateService templateService,
            NotificationService notificationService, EventDefinitionService eventDefinitionService,
            EventActionService eventActionService, ActionService actionService, WorkflowService workflowService,
            CoreFunctionService coreFunctionService, CoreSchemaService coreSchemaService,
            CoreFillerService coreFillerService) {

        super(draftService, securityService, messageResourceService,
                List.<AbstractOverridableDataService<?, ?>>of(storageService, connectionService, templateService,
                        notificationService, eventDefinitionService, eventActionService, actionService,
                        workflowService, coreFunctionService, coreSchemaService, coreFillerService));
    }
}
