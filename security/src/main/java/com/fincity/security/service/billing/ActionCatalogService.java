package com.fincity.security.service.billing;

import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.security.dao.billing.ActionCatalogDAO;
import com.fincity.security.dto.billing.ActionCatalog;
import com.fincity.security.jooq.tables.records.SecurityActionCatalogRecord;

import reactor.core.publisher.Mono;

/**
 * CRUD for the platform action catalog (master list of meterable actions).
 */
@Service
public class ActionCatalogService extends
        AbstractJOOQUpdatableDataService<SecurityActionCatalogRecord, ULong, ActionCatalog, ActionCatalogDAO> {

    public ActionCatalogService(ActionCatalogDAO dao) {
        this.dao = dao;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Action_Catalog_CREATE')")
    public Mono<ActionCatalog> create(ActionCatalog entity) {
        return super.create(entity);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Action_Catalog_READ')")
    public Mono<ActionCatalog> read(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAuthority('Authorities.Action_Catalog_READ')")
    public Mono<ActionCatalog> findByActionKey(String actionKey) {
        return this.dao.findByActionKey(actionKey);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Action_Catalog_UPDATE')")
    public Mono<ActionCatalog> update(ActionCatalog entity) {
        return super.update(entity);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Action_Catalog_DELETE')")
    public Mono<Integer> delete(ULong id) {
        return super.delete(id);
    }

    @Override
    protected Mono<ActionCatalog> updatableEntity(ActionCatalog entity) {
        return this.read(entity.getId()).map(existing -> existing
                .setName(entity.getName())
                .setDescription(entity.getDescription())
                .setDefaultActionClass(entity.getDefaultActionClass())
                .setDefaultUnitCost(entity.getDefaultUnitCost())
                .setUnitName(entity.getUnitName())
                .setStatus(entity.getStatus()));
    }
}
