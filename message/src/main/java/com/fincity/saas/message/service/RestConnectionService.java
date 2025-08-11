package com.fincity.saas.message.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.base.BaseConnectionService;

@Service
public class RestConnectionService extends BaseConnectionService {

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.REST_API;
    }
}
