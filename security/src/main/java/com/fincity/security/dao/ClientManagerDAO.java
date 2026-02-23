package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientManager.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public Mono<Integer> createIfNotExists(ULong clientId, ULong managerId, ULong createdBy) {
        return Mono.from(this.dslContext
                .insertInto(SECURITY_CLIENT_MANAGER)
                .set(SECURITY_CLIENT_MANAGER.CLIENT_ID, clientId)
                .set(SECURITY_CLIENT_MANAGER.MANAGER_ID, managerId)
                .set(SECURITY_CLIENT_MANAGER.CREATED_BY, createdBy)
                .onDuplicateKeyIgnore());
    }

    public Mono<Integer> deleteByClientIdAndManagerId(ULong clientId, ULong managerId) {
        return Mono.from(this.dslContext
                .deleteFrom(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER
                        .CLIENT_ID
                        .eq(clientId)
                        .and(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId))));
    }

    public Mono<Page<ULong>> getClientsOfManager(ULong managerId, Pageable pageable) {

        Mono<Integer> countMono = Mono.from(this.dslContext
                        .selectCount()
                        .from(SECURITY_CLIENT_MANAGER)
                        .where(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId)))
                .map(Record1::value1);

        Mono<List<ULong>> listMono = Flux.from(this.dslContext
                        .select(SECURITY_CLIENT_MANAGER.CLIENT_ID)
                        .from(SECURITY_CLIENT_MANAGER)
                        .where(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId))
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset()))
                .map(r -> r.get(SECURITY_CLIENT_MANAGER.CLIENT_ID))
                .collectList();

        return listMono.flatMap(
                list -> countMono.map(count -> PageableExecutionUtils.getPage(list, pageable, () -> count)));
    }

    public Mono<Boolean> isManagerForClient(ULong managerId, ULong clientId) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(SECURITY_CLIENT_MANAGER)
                        .where(SECURITY_CLIENT_MANAGER
                                .MANAGER_ID
                                .eq(managerId)
                                .and(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientId))))
                .map(r -> r.value1() > 0);
    }

    public Mono<Map<ULong, Collection<ULong>>> getManagerIds(Set<ULong> clientIds) {
        return Flux.from(this.dslContext.select(SECURITY_CLIENT_MANAGER.CLIENT_ID, SECURITY_CLIENT_MANAGER.MANAGER_ID)
                .from(SECURITY_CLIENT_MANAGER)
                .where(SECURITY_CLIENT_MANAGER.CLIENT_ID.in(clientIds)))
                .collectMultimap(r -> r.get(SECURITY_CLIENT_MANAGER.CLIENT_ID),
                        r -> r.get(SECURITY_CLIENT_MANAGER.MANAGER_ID));
    }

    public Mono<List<ULong>> getClientIdsOfManager(ULong managerId) {
        return Flux.from(this.dslContext
                        .select(SECURITY_CLIENT_MANAGER.CLIENT_ID)
                        .from(SECURITY_CLIENT_MANAGER)
                        .where(SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(managerId)))
                .map(r -> r.get(SECURITY_CLIENT_MANAGER.CLIENT_ID))
                .collectList();
    }

    public Mono<ULong> getLatestManagerIdForClient(ULong clientId) {
        return Mono.from(this.dslContext
                        .select(SECURITY_CLIENT_MANAGER.MANAGER_ID)
                        .from(SECURITY_CLIENT_MANAGER)
                        .where(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientId))
                        .orderBy(SECURITY_CLIENT_MANAGER.CREATED_AT.desc())
                        .limit(1))
                .map(r -> r.get(SECURITY_CLIENT_MANAGER.MANAGER_ID));
    }
}
