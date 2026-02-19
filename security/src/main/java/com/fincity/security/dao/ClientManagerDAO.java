package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientManager.SECURITY_CLIENT_MANAGER;

import java.util.List;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

import com.fincity.security.dao.clientcheck.AbstractClientCheckDAO;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.jooq.tables.records.SecurityClientManagerRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientManagerDAO extends AbstractClientCheckDAO<SecurityClientManagerRecord, ULong, ClientManager> {

    public ClientManagerDAO() {
        super(ClientManager.class, SECURITY_CLIENT_MANAGER, SECURITY_CLIENT_MANAGER.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_CLIENT_MANAGER.CLIENT_ID;
    }

    public Mono<ClientManager> readByClientIdAndManagerId(ULong clientId, ULong managerId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientId)
                        .and(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId))))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<Integer> createIfNotExists(ULong clientId, ULong managerId, ULong createdBy) {
        return Mono.from(this.dslContext.insertInto(SECURITY_CLIENT_MANAGER)
                .set(SECURITY_CLIENT_MANAGER.CLIENT_ID, clientId)
                .set(SECURITY_CLIENT_MANAGER.MANAGER_ID, managerId)
                .set(SECURITY_CLIENT_MANAGER.CREATED_BY, createdBy)
                .onDuplicateKeyIgnore());
    }

    public Mono<Integer> deleteByClientIdAndManagerId(ULong clientId, ULong managerId) {
        return Mono.from(this.dslContext.deleteFrom(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientId)
                        .and(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId))));
    }

    public Mono<Page<ULong>> getClientsOfManager(ULong managerId, Pageable pageable) {

        Mono<Integer> countMono = Mono.from(this.dslContext.selectCount()
                .from(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId)))
                .map(Record1::value1);

        Mono<List<ULong>> listMono = Flux.from(this.dslContext.select(SECURITY_CLIENT_MANAGER.CLIENT_ID)
                .from(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId))
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset()))
                .map(r -> r.get(SECURITY_CLIENT_MANAGER.CLIENT_ID))
                .collectList();

        return listMono.flatMap(list -> countMono
                .map(count -> PageableExecutionUtils.getPage(list, pageable, () -> count)));
    }

    public Mono<Boolean> isManagerForClient(ULong managerId, ULong clientId) {
        return Mono.from(this.dslContext.selectCount()
                .from(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId)
                        .and(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientId))))
                .map(r -> r.value1() > 0);
    }
}
