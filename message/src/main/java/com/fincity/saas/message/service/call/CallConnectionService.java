package com.fincity.saas.message.service.call;

import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.base.BaseConnectionService;
import org.springframework.stereotype.Service;

@Service
public class CallConnectionService extends BaseConnectionService {

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.CALL;
    }
}
