package com.fincity.security.dao;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.jooq.tables.records.SecurityClientManagerRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.fincity.security.jooq.tables.SecurityClientManager.SECURITY_CLIENT_MANAGER;

@Component
public class ClientManagerDAO extends AbstractDAO<SecurityClientManagerRecord, ULong, ClientManager> {

    public ClientManagerDAO() {
        super(ClientManager.class, SECURITY_CLIENT_MANAGER, SECURITY_CLIENT_MANAGER.ID);
    }

    public Mono<ClientManager> readByClientIdAndManagerId(ULong clientId, ULong managerId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientId)
                        .and(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId))))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<Boolean> hasAnyOtherClientAssociations(ULong managerId, ULong excludeId) {
        return Mono.from(this.dslContext.selectCount()
                .from(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId)
                        .and(SECURITY_CLIENT_MANAGER.ID.ne(excludeId))))
                .map(rec -> rec.value1() > 0)
                .defaultIfEmpty(false);
    }
}
