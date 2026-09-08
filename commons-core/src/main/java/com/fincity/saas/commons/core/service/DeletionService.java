package com.fincity.saas.commons.core.service;

import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.mongo.service.AbstractDeletionService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DeletionService extends AbstractDeletionService {

    private final List<AbstractOverridableDataService<?, ?>> serviceList;

    private final AppDataService appDataService;

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
            WorkflowService workflowService,
            AppDataService appDataService) {
        super(feignSecurityService);
        this.appDataService = appDataService;
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

    /**
     * Definitions first, then the app's draft data.
     *
     * The base implementation only removes definitions, versions and caches, so
     * without this an app's draft database survived the app itself and sat on the
     * cluster unreachable. Ordered after the definition delete so a failure there
     * leaves the data in place rather than half-removed.
     *
     * The LIVE database is deliberately left alone: orphaning it predates this work
     * and removing customer data on app deletion is a separate decision.
     */
    @Override
    public Mono<Boolean> deleteEverything(
            String forwardedHost, String forwardedPort, String clientCode, String headerAppCode, String appCode) {

        return super.deleteEverything(forwardedHost, forwardedPort, clientCode, headerAppCode, appCode)
                .flatMap(deleted -> SecurityContextUtil.getUsersContextAuthentication()
                        .flatMap(ca -> this.appDataService.dropDraftData(appCode, ca.getClientCode()))
                        .thenReturn(deleted));
    }
}
