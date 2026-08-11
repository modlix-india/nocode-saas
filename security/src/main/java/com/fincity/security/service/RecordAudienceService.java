package com.fincity.security.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.OrgStructure;
import com.fincity.security.dto.User;
import com.fincity.security.model.RecordAudienceRequest;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Answers "which users are entitled to a record owned by this client and assigned to this person?".
 *
 * <p>The inverse of the condition an owning service puts on its reads. It lives here, rather than in
 * the service that owns the records, because every input is security's: the reporting tree and who
 * holds the Owner role. Asking it from outside would mean several round trips and a second copy of
 * how a sub-organisation is defined.
 *
 * <h2>The rule being inverted</h2>
 *
 * <p>An owning service filters reads with, in substance:
 *
 * <pre>{@code
 * clientId IN managingClientIds(caller)     -- caller's client manages the record's client
 * OR assignedUserId IN subOrg(caller)       -- the assignee reports to the caller, transitively
 * OR createdBy IN subOrg(caller)            -- for an outside (business partner) caller
 * }</pre>
 *
 * <p>Read forwards, each of those expands a set around the <i>caller</i>. Read backwards they
 * collapse around the <i>record</i>, and that is much cheaper:
 *
 * <ul>
 *   <li>{@code assignedUserId IN subOrg(u)} holds exactly when {@code u} is the assignee or one of
 *       their managers. That is a walk up a chain, bounded by the depth of the organisation, not by
 *       its size.
 *   <li>An Owner is entitled to their whole client, so every Owner of the record's client
 *       qualifies regardless of reporting lines.
 * </ul>
 *
 * <h2>What this does not cover</h2>
 *
 * <p>Anything an owning service adds on top of the shared rule. Entity-processor grants read access
 * through per-product rules as well, and those are its own data; it unions them onto this result.
 * Callers must treat this as "the audience from reporting lines and the Owner role", not "the
 * audience".
 *
 * <p>And the cross-client business-partner branch, for the reason given on {@link #ownersOf}.
 */
@Service
public class RecordAudienceService {

    private static final Logger logger = LoggerFactory.getLogger(RecordAudienceService.class);

    private final OrgStructureService orgStructureService;
    private final ClientService clientService;
    private final UserDAO userDAO;

    public RecordAudienceService(
            OrgStructureService orgStructureService, ClientService clientService, UserDAO userDAO) {
        this.orgStructureService = orgStructureService;
        this.clientService = clientService;
        this.userDAO = userDAO;
    }

    public Mono<List<ULong>> resolve(RecordAudienceRequest request) {

        if (request == null) return Mono.just(List.of());

        return this.clientIdOf(request)
                .flatMap(clientId -> this.resolveFor(clientId, request))
                .defaultIfEmpty(List.of())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RecordAudienceService.resolve"));
    }

    /**
     * The owning client, from whichever of the two identifiers the caller had.
     *
     * <p>Empty when neither is usable, which collapses to an empty audience upstream. That is the
     * intended fail-closed behaviour, but it is also indistinguishable from a legitimately empty
     * answer, which is precisely how the id-only version of this went unnoticed. Anything that
     * cannot identify a client is worth a log line rather than a silent shrug.
     */
    private Mono<ULong> clientIdOf(RecordAudienceRequest request) {

        if (request.getClientId() != null) return Mono.just(ULongUtil.valueOf(request.getClientId()));

        if (StringUtil.safeIsBlank(request.getClientCode())) {
            logger.warn("Audience asked for without a client id or code; telling nobody.");
            return Mono.empty();
        }

        return this.clientService
                .getClientId(request.getClientCode())
                .switchIfEmpty(Mono.fromRunnable(() -> logger.warn(
                        "No client for code {}; telling nobody about its records.", request.getClientCode())));
    }

    private Mono<List<ULong>> resolveFor(ULong clientId, RecordAudienceRequest request) {

        ULong assignedUserId = request.getAssignedUserId() == null
                ? null
                : ULongUtil.valueOf(request.getAssignedUserId());
        ULong createdBy = request.getCreatedBy() == null ? null : ULongUtil.valueOf(request.getCreatedBy());

        return Mono.zip(
                        this.orgStructureService.getOrgStructure(clientId),
                        this.ownersOf(clientId))
                .map(tuple -> {
                    OrgStructure tree = tuple.getT1();
                    Set<ULong> audience = new LinkedHashSet<>(tuple.getT2());

                    // Both user columns, because an owning service picks between them by caller
                    // type and this has no caller. A business partner's reporting chain and the
                    // assignee's are both entitled, so both go in.
                    audience.addAll(tree.selfAndManagers(assignedUserId));
                    audience.addAll(tree.selfAndManagers(createdBy));

                    if (!request.isActiveOnly()) return List.copyOf(audience);

                    // Deactivated people stay in the tree on purpose, so a manager keeps seeing a
                    // departed report's records. They should not be sent notifications, and a user
                    // the tree has never heard of reads as inactive, so this fails closed.
                    Set<ULong> live = new LinkedHashSet<>();
                    for (ULong id : audience) if (tree.isActive(id) || !tree.contains(id)) live.add(id);
                    return List.copyOf(live);
                });
    }

    /**
     * Owners of the record's own client.
     *
     * <p>An Owner's sub-organisation is their entire client, so they are entitled to everything in
     * it without appearing in anybody's reporting chain. That is the whole of the Owner branch.
     *
     * <p><b>Deliberately not walked up the client hierarchy.</b> The first version of this did walk
     * it, on the reasoning that a user whose client manages this one matches the
     * {@code clientId IN managingClientIds} branch. Measured against a real tenant it returned 64
     * users for a record with no assignee, almost all of them owners of the parent client, and none
     * of them can actually read those rows: the owning service also filters the record's client
     * <i>code</i> against the caller's effective client code, and a parent-client user's does not
     * match. Sending them a customer's message would have been a disclosure, not a nuisance.
     *
     * <p>What that leaves uncovered is a business-partner manager in a parent client whose effective
     * client code <i>is</i> this one. They will not be notified. Deciding that needs the record's
     * client code and the caller's partner role, neither of which this request carries, so it fails
     * closed until it does.
     *
     * <p>{@code getOwners} already filters to active users, which is what an audience wants.
     */
    private Mono<Set<ULong>> ownersOf(ULong clientId) {

        return this.userDAO
                .getOwners(clientId)
                .map(owners -> owners.stream()
                        .map(User::getId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
                .map(ids -> (Set<ULong>) ids)
                .defaultIfEmpty(Set.of());
    }
}
