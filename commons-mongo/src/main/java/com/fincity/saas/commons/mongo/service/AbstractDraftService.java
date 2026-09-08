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
                // baseVersion is deliberately NOT re-stamped on an existing draft.
                // It records the live version the draft was taken FROM, so freezing
                // it is what makes the optimistic-lock check on publish mean
                // something: if the live object moved on while a draft sat here, the
                // publish fails instead of silently overwriting the newer live
                // content with work derived from an older copy. Re-stamping made the
                // check compare a version against itself and always pass.
                //
                // Recoverable rather than terminal: discard the draft and save again
                // and the new row takes the current live version as its base.
                .map(existing -> existing.setContent(content)
                        .setMessage(message)
                        .setObjectId(objectId)
                        // The draft's own counter moves on every save, so the next
                        // writer's expected version no longer matches. The comparison
                        // itself lives in AbstractOverridableDataService.saveDraft:
                        // this class is storage with no policy and no authorization,
                        // which is what keeps it from becoming a second gate that can
                        // drift from the first.
                        .setVersion(existing.getVersion() + 1))
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

    /**
     * One object type's drafts. deleteEverything runs once per overridable service,
     * so the type-scoped form is what each of them should call: the unscoped one
     * would have the first service wipe every other service's drafts too, which
     * happens to be harmless today only because they all run in the same sweep.
     */
    public Mono<Long> deleteBy(String objectAppCode, String objectType, String clientCode) {
        return this.repo.deleteByObjectAppCodeAndObjectTypeAndClientCode(objectAppCode, objectType, clientCode);
    }
}
