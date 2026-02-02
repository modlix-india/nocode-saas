package com.fincity.saas.entity.processor.oserver.core.service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.oserver.core.document.Connection;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class BaseConnectionService extends AbstractCoreService<Connection> {

    protected String getObjectName() {
        return Connection.class.getSimpleName();
    }

    public abstract ConnectionType getConnectionType();

    @Override
    protected Mono<Connection> fetchCoreDocument(
            String appCode, String urlClientCode, String clientCode, String documentName) {
        return super.coreService
                .getConnection(
                        documentName,
                        appCode,
                        clientCode,
                        urlClientCode,
                        getConnectionType().name())
                .switchIfEmpty(super.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.CONNECTION_NOT_FOUND,
                        documentName));
    }
}
