package com.fincity.saas.entity.processor.oserver.core.service;

import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import org.springframework.stereotype.Service;

@Service
public class MessageConnectionService extends BaseConnectionService {

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.TEXT;
    }
}
