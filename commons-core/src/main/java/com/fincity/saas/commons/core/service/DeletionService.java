package com.fincity.saas.commons.core.service;

import com.fincity.saas.commons.mongo.service.AbstractDeletionService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeletionService extends AbstractDeletionService {
    private final List<AbstractOverridableDataService<?, ?>> serviceList;

    public DeletionService(
            IFeignSecurityService feignSecurityService,
            ActionService actionService,
            TemplateService templateService,
            StorageService storageService,
            CoreFunctionService funService,
            CoreSchemaService schemaService,
            EventActionService evaService,
            EventDefinitionService edService,
            CoreFillerService fillerService,
            ConnectionService connectionService,
            TransportService transportService,
            WorkflowService workflowService) {
        super(feignSecurityService);
        serviceList = List.of(
                actionService,
                templateService,
                storageService,
                funService,
                schemaService,
                evaService,
                edService,
                fillerService,
                connectionService,
                transportService,
                workflowService);
    }

    @Override
    public List<AbstractOverridableDataService<?, ?>> getServices() {
        return this.serviceList;
    }
}
