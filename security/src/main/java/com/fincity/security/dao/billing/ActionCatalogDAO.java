package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.tables.SecurityActionCatalog.SECURITY_ACTION_CATALOG;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.ActionCatalog;
import com.fincity.security.jooq.tables.records.SecurityActionCatalogRecord;

import reactor.core.publisher.Mono;

@Component
public class ActionCatalogDAO extends AbstractUpdatableDAO<SecurityActionCatalogRecord, ULong, ActionCatalog> {

    public ActionCatalogDAO() {
        super(ActionCatalog.class, SECURITY_ACTION_CATALOG, SECURITY_ACTION_CATALOG.ID);
    }

    public Mono<ActionCatalog> findByActionKey(String actionKey) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_ACTION_CATALOG)
                .where(SECURITY_ACTION_CATALOG.ACTION_KEY.eq(actionKey)))
                .map(r -> r.into(ActionCatalog.class));
    }
}
