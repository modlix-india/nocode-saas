package com.fincity.saas.entity.collector.service;

import com.fincity.saas.entity.collector.dao.EntityIntegrationDAO;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EntityIntegrationService {

    private final EntityIntegrationDAO dao;

    public EntityIntegration createIntegration(EntityIntegration entity) {
        return dao.insert(entity);
    }

    public List<EntityIntegration> getAll() {
        return dao.findAll();
    }

    public EntityIntegration getById(Long id) {
        return dao.findById(id)
                .orElseThrow(() -> new RuntimeException("EntityIntegration not found for id: " + id));
    }

    public void delete(Long id) {
        dao.deleteById(id);
    }

    public List<EntityIntegration> findByClientAndApp(String clientCode, String appCode) {
        return dao.findByClientCodeAndAppCode(clientCode, appCode);
    }

    public EntityIntegration updateIntegration(EntityIntegration entity) {
        return dao.update(entity);
    }

    public List<EntityIntegration> getByClientCode(String clientCode) {
        return dao.findByClientCode(clientCode);
    }

}
