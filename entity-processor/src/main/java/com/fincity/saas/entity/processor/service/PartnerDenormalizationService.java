package com.fincity.saas.entity.processor.service;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PARTNERS;
import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKETS;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.ClientDenormData;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PartnerDenormalizationService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerDenormalizationService.class);

    @Value("${partner.denorm.batch-size:100}")
    private int batchSize;

    @Value("${partner.denorm.tenant-delay-ms:500}")
    private long tenantDelayMs;

    private final DSLContext dslContext;
    private final IFeignSecurityService securityService;

    public PartnerDenormalizationService(DSLContext dslContext, IFeignSecurityService securityService) {
        this.dslContext = dslContext;
        this.securityService = securityService;
    }

    public Mono<Integer> syncDelta() {
        logger.info("Starting partner denorm delta sync");

        return syncTicketCountsDelta()
                .flatMap(ticketUpdated -> syncSecurityDataDelta()
                        .map(securityUpdated -> ticketUpdated + securityUpdated))
                .doOnSuccess(count -> logger.info("Delta sync complete, {} partners updated", count))
                .onErrorResume(e -> {
                    logger.error("Delta sync failed: {}", e.getMessage(), e);
                    return Mono.just(0);
                });
    }

    public Mono<Integer> syncFull() {
        logger.info("Starting partner denorm full sync");

        return syncTicketCountsFull()
                .flatMap(ticketUpdated -> syncSecurityDataFull()
                        .map(securityUpdated -> Math.max(ticketUpdated, securityUpdated)))
                .doOnSuccess(count -> logger.info("Full sync complete, {} partners updated", count))
                .onErrorResume(e -> {
                    logger.error("Full sync failed: {}", e.getMessage(), e);
                    return Mono.just(0);
                });
    }

    // ---- Ticket count sync ----

    private Mono<Integer> syncTicketCountsDelta() {
        // Find partners whose tickets changed since last denorm
        return Flux.from(
                dslContext.select(
                        ENTITY_PROCESSOR_PARTNERS.ID,
                        ENTITY_PROCESSOR_PARTNERS.APP_CODE,
                        ENTITY_PROCESSOR_PARTNERS.CLIENT_CODE,
                        ENTITY_PROCESSOR_PARTNERS.CLIENT_ID)
                        .from(ENTITY_PROCESSOR_PARTNERS)
                        .where(DSL.exists(
                                dslContext.selectOne()
                                        .from(ENTITY_PROCESSOR_TICKETS)
                                        .where(ENTITY_PROCESSOR_TICKETS.APP_CODE.eq(ENTITY_PROCESSOR_PARTNERS.APP_CODE)
                                                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_CODE
                                                        .eq(ENTITY_PROCESSOR_PARTNERS.CLIENT_CODE))
                                                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID
                                                        .eq(ENTITY_PROCESSOR_PARTNERS.CLIENT_ID))
                                                .and(ENTITY_PROCESSOR_PARTNERS.DENORM_UPDATED_AT.isNull()
                                                        .or(ENTITY_PROCESSOR_TICKETS.CREATED_AT
                                                                .greaterThan(ENTITY_PROCESSOR_PARTNERS.DENORM_UPDATED_AT))
                                                        .or(ENTITY_PROCESSOR_TICKETS.UPDATED_AT
                                                                .greaterThan(ENTITY_PROCESSOR_PARTNERS.DENORM_UPDATED_AT)))))))
                .collectList()
                .flatMap(partners -> {
                    if (partners.isEmpty()) return Mono.just(0);

                    List<ULong> partnerIds = partners.stream()
                            .map(r -> r.get(ENTITY_PROCESSOR_PARTNERS.ID))
                            .toList();

                    return recountTicketsForPartners(partnerIds);
                });
    }

    private Mono<Integer> syncTicketCountsFull() {
        // Recount all tickets grouped by partner
        return Flux.from(
                dslContext.select(
                        ENTITY_PROCESSOR_PARTNERS.ID,
                        DSL.coalesce(
                                DSL.field(dslContext.selectCount()
                                        .from(ENTITY_PROCESSOR_TICKETS)
                                        .where(ENTITY_PROCESSOR_TICKETS.APP_CODE
                                                .eq(ENTITY_PROCESSOR_PARTNERS.APP_CODE)
                                                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_CODE
                                                        .eq(ENTITY_PROCESSOR_PARTNERS.CLIENT_CODE))
                                                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID
                                                        .eq(ENTITY_PROCESSOR_PARTNERS.CLIENT_ID)))),
                                DSL.zero()).as("ticket_count"))
                        .from(ENTITY_PROCESSOR_PARTNERS))
                .collectList()
                .flatMap(rows -> {
                    if (rows.isEmpty()) return Mono.just(0);

                    LocalDateTime now = LocalDateTime.now();
                    var batch = dslContext.batch(
                            dslContext.update(ENTITY_PROCESSOR_PARTNERS)
                                    .set(ENTITY_PROCESSOR_PARTNERS.TOTAL_TICKETS, (ULong) null)
                                    .set(ENTITY_PROCESSOR_PARTNERS.DENORM_UPDATED_AT, (LocalDateTime) null)
                                    .where(ENTITY_PROCESSOR_PARTNERS.ID.eq((ULong) null)));

                    for (var row : rows) {
                        ULong id = row.get(ENTITY_PROCESSOR_PARTNERS.ID);
                        int count = row.get("ticket_count", Integer.class);
                        batch.bind(ULong.valueOf(count), now, id);
                    }

                    return Mono.from(batch)
                            .map(results -> rows.size());
                });
    }

    private Mono<Integer> recountTicketsForPartners(List<ULong> partnerIds) {
        // For each partner, count its tickets and update
        return Flux.from(
                dslContext.select(
                        ENTITY_PROCESSOR_PARTNERS.ID,
                        ENTITY_PROCESSOR_PARTNERS.APP_CODE,
                        ENTITY_PROCESSOR_PARTNERS.CLIENT_CODE,
                        ENTITY_PROCESSOR_PARTNERS.CLIENT_ID)
                        .from(ENTITY_PROCESSOR_PARTNERS)
                        .where(ENTITY_PROCESSOR_PARTNERS.ID.in(partnerIds)))
                .collectList()
                .flatMap(partners -> {
                    if (partners.isEmpty()) return Mono.just(0);

                    LocalDateTime now = LocalDateTime.now();

                    return Flux.fromIterable(partners)
                            .flatMap(p -> Mono.from(
                                    dslContext.selectCount()
                                            .from(ENTITY_PROCESSOR_TICKETS)
                                            .where(ENTITY_PROCESSOR_TICKETS.APP_CODE.eq(p.get(ENTITY_PROCESSOR_PARTNERS.APP_CODE))
                                                    .and(ENTITY_PROCESSOR_TICKETS.CLIENT_CODE.eq(p.get(ENTITY_PROCESSOR_PARTNERS.CLIENT_CODE)))
                                                    .and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.eq(p.get(ENTITY_PROCESSOR_PARTNERS.CLIENT_ID)))))
                                    .flatMap(count -> Mono.from(
                                            dslContext.update(ENTITY_PROCESSOR_PARTNERS)
                                                    .set(ENTITY_PROCESSOR_PARTNERS.TOTAL_TICKETS, ULong.valueOf(count.value1()))
                                                    .set(ENTITY_PROCESSOR_PARTNERS.DENORM_UPDATED_AT, now)
                                                    .where(ENTITY_PROCESSOR_PARTNERS.ID.eq(p.get(ENTITY_PROCESSOR_PARTNERS.ID))))))
                            .collectList()
                            .map(List::size);
                });
    }

    // ---- Security data sync ----

    private Mono<Integer> syncSecurityDataDelta() {
        // Get oldest DENORM_UPDATED_AT as the since timestamp
        return Mono.from(
                dslContext.select(DSL.min(ENTITY_PROCESSOR_PARTNERS.DENORM_UPDATED_AT))
                        .from(ENTITY_PROCESSOR_PARTNERS))
                .flatMap(rec -> {
                    LocalDateTime since = rec.value1();
                    // If any partner has never been synced, null min means we skip delta
                    // (full sync will catch those)
                    if (since == null) return Mono.just(0);

                    return getAllPartnerClientIds()
                            .flatMap(clientIds -> callSecurityDelta(clientIds, since));
                });
    }

    private Mono<Integer> syncSecurityDataFull() {
        return getAllPartnerClientIds()
                .flatMap(this::callSecurityFull);
    }

    private Mono<List<BigInteger>> getAllPartnerClientIds() {
        return Flux.from(
                dslContext.selectDistinct(ENTITY_PROCESSOR_PARTNERS.CLIENT_ID)
                        .from(ENTITY_PROCESSOR_PARTNERS))
                .map(r -> r.get(ENTITY_PROCESSOR_PARTNERS.CLIENT_ID).toBigInteger())
                .collectList();
    }

    private Mono<Integer> callSecurityFull(List<BigInteger> allClientIds) {
        if (allClientIds.isEmpty()) return Mono.just(0);

        return Flux.fromIterable(partition(allClientIds, batchSize))
                .concatMap(batch -> securityService.getClientsDenormFull(batch)
                        .flatMap(this::applySecurityData))
                .reduce(Integer.valueOf(0), (a, b) -> a + b);
    }

    private Mono<Integer> callSecurityDelta(List<BigInteger> allClientIds, LocalDateTime since) {
        if (allClientIds.isEmpty()) return Mono.just(0);

        String sinceStr = since.toString();

        return Flux.fromIterable(partition(allClientIds, batchSize))
                .concatMap(batch -> securityService.getClientsDenormDelta(batch, sinceStr)
                        .flatMap(this::applySecurityData))
                .reduce(Integer.valueOf(0), (a, b) -> a + b);
    }

    private Mono<Integer> applySecurityData(Map<BigInteger, ClientDenormData> data) {
        if (data == null || data.isEmpty()) return Mono.just(0);

        LocalDateTime now = LocalDateTime.now();

        return Flux.fromIterable(data.entrySet())
                .flatMap(entry -> {
                    ULong clientId = ULong.valueOf(entry.getKey());
                    ClientDenormData d = entry.getValue();

                    return Mono.from(
                            dslContext.update(ENTITY_PROCESSOR_PARTNERS)
                                    .set(ENTITY_PROCESSOR_PARTNERS.CLIENT_NAME, d.getClientName())
                                    .set(ENTITY_PROCESSOR_PARTNERS.ACTIVE_USERS,
                                            UInteger.valueOf(d.getActiveUsers()))
                                    .set(ENTITY_PROCESSOR_PARTNERS.USER_NAMES, d.getUserNames())
                                    .set(ENTITY_PROCESSOR_PARTNERS.USER_PHONES, d.getUserPhones())
                                    .set(ENTITY_PROCESSOR_PARTNERS.CLIENT_MANAGER_IDS, d.getClientManagerIds())
                                    .set(ENTITY_PROCESSOR_PARTNERS.DENORM_UPDATED_AT, now)
                                    .where(ENTITY_PROCESSOR_PARTNERS.CLIENT_ID.eq(clientId)));
                })
                .collectList()
                .map(List::size);
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size)
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        return partitions;
    }
}
