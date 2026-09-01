package com.fincity.saas.commons.mongo.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.mongo.repository.DraftRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Storage for unpublished work. Deliberately has no authorization of its own and
 * no controller: every entry point goes through the owning
 * AbstractOverridableDataService, which already runs accessCheck for the object
 * in question. Adding a second, independent authorization path over the same data
 * is how the two drift apart.
 */
public abstract class AbstractDraftService extends AbstractMongoDataService<String, Draft, DraftRepository> {

    protected AbstractDraftService() {
        super(Draft.class);
    }

    /**
     * One draft per (app, type, name, client). A second save replaces the first
     * rather than accumulating, which is what the unique index on the document
     * enforces at the storage level.
     */
    public Mono<Draft> upsert(String objectType, String objectAppCode, String objectName, String clientCode,
            String objectId, Map<String, Object> content, int baseVersion, String message) {

        return this.repo
                .findOneByObjectAppCodeAndObjectTypeAndObjectNameAndClientCode(objectAppCode, objectType, objectName,
                        clientCode)
                .map(existing -> existing.setContent(content)
                        .setBaseVersion(baseVersion)
                        .setMessage(message)
                        .setObjectId(objectId))
                .switchIfEmpty(Mono.fromSupplier(() -> new Draft().setObjectType(objectType)
                        .setObjectAppCode(objectAppCode)
                        .setObjectName(objectName)
                        .setClientCode(clientCode)
                        .setObjectId(objectId)
                        .setContent(content)
                        .setBaseVersion(baseVersion)
                        .setMessage(message)))
                .map(draft -> {
                    draft.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                    return draft;
                })
                .flatMap(this.repo::save);
    }

    public Mono<Draft> find(String objectType, String objectAppCode, String objectName, String clientCode) {
        return this.repo.findOneByObjectAppCodeAndObjectTypeAndObjectNameAndClientCode(objectAppCode, objectType,
                objectName, clientCode);
    }

    public Mono<Draft> findByObjectId(String objectId) {
        return this.repo.findOneByObjectId(objectId);
    }

    public Flux<Draft> pending(String objectAppCode, String clientCode) {
        return this.repo.findByObjectAppCodeAndClientCode(objectAppCode, clientCode);
    }

    public Flux<Draft> pending(String objectAppCode, String objectType, String clientCode) {
        return this.repo.findByObjectAppCodeAndObjectTypeAndClientCode(objectAppCode, objectType, clientCode);
    }

    /**
     * @return true when a draft existed and was removed, false when there was
     *         nothing to discard. Callers distinguish the two.
     */
    public Mono<Boolean> discard(String objectType, String objectAppCode, String objectName, String clientCode) {
        return this.repo
                .findOneByObjectAppCodeAndObjectTypeAndObjectNameAndClientCode(objectAppCode, objectType, objectName,
                        clientCode)
                .flatMap(draft -> this.repo.delete(draft).thenReturn(Boolean.TRUE))
                .defaultIfEmpty(Boolean.FALSE);
    }

    public Mono<Boolean> discardByObjectId(String objectId) {
        return this.repo.findOneByObjectId(objectId)
                .flatMap(draft -> this.repo.delete(draft).thenReturn(Boolean.TRUE))
                .defaultIfEmpty(Boolean.FALSE);
    }

    /**
     * Called when an app is wiped, alongside the equivalent on the version service.
     */
    public Mono<Long> deleteEverything(String objectAppCode, String clientCode) {
        return this.repo.deleteByObjectAppCodeAndClientCode(objectAppCode, clientCode);
    }
}
