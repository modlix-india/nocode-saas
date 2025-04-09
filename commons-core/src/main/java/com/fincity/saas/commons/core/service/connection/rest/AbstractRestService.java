package com.fincity.saas.commons.core.service.connection.rest;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.dto.RestRequest;
import com.fincity.saas.commons.core.dto.RestResponse;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class AbstractRestService implements IRestService {

    protected Gson gson;
    protected CoreMessageResourceService msgService;

    @Autowired
    private void setMsgService(CoreMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    private void setGson(Gson gson) {
        this.gson = gson;
    }

    @Override
    public Mono<RestResponse> call(Connection connection, RestRequest request) {
        return this.call(connection, request, false);
    }
}
