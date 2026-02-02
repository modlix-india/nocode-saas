package com.fincity.saas.entity.processor.oserver.core.service;

import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ConnectionServiceProvider {

    private final Map<ConnectionType, BaseConnectionService> servicesByType;

    public ConnectionServiceProvider(AutowireCapableBeanFactory beanFactory) {
        EnumMap<ConnectionType, BaseConnectionService> map = new EnumMap<>(ConnectionType.class);
        for (ConnectionType type : ConnectionType.values()) {
            BaseConnectionService svc = new GenericConnectionService(type);
            beanFactory.autowireBean(svc);
            map.put(type, svc);
        }
        this.servicesByType = map;
    }

    public BaseConnectionService getService(ConnectionType type) {
        return this.servicesByType.get(type);
    }

    public Map<ConnectionType, BaseConnectionService> getAll() {
        return this.servicesByType;
    }

    private static final class GenericConnectionService extends BaseConnectionService {
        private final ConnectionType type;

        private GenericConnectionService(ConnectionType type) {
            this.type = type;
        }

        @Override
        public ConnectionType getConnectionType() {
            return this.type;
        }
    }
}
