package com.fincity.saas.commons.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.core.document.CoreSchema;
import com.fincity.saas.commons.core.repository.CoreSchemaDocumentRepository;
import com.fincity.saas.commons.mongo.service.AbstractSchemaService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.Gson;

import java.util.List;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CoreSchemaService extends AbstractSchemaService<CoreSchema, CoreSchemaDocumentRepository> {
    /** Draftable, like every other core object. See StorageService for why. */
    @Override
    protected boolean isDraftable() {
        return true;
    }

    /**
     * Lazy on purpose: StorageService takes CoreSchemaService in its constructor, so
     * a normal injection here is a cycle. Only used to rebuild tables after a save.
     */
    @Autowired
    @Lazy
    private StorageService storageService;

    protected CoreSchemaService(FeignAuthenticationService feignAuthenticationService, Gson gson) {
        super(CoreSchema.class, feignAuthenticationService, gson);
    }

    @Override
    public String getObjectName() {
        return "Schema";
    }

    /**
     * Every path that changes this schema's live definition rebuilds whatever was
     * standing on it.
     *
     * Hooked here rather than on each method, and that is the point. The mutation
     * surface is create, update, updateBlueprint and publish, and it will grow; a
     * rebuild wired to the ones that existed when it was written is a rebuild that
     * silently stops covering the next one. Every one of them already ends here,
     * because every one of them has to drop the same caches.
     *
     * A storage may be written as nothing more than a reference to a schema, or have
     * individual fields that are, so editing a schema edits the shape of a table
     * nobody touched. Nothing else in the chain notices: the storage's version has
     * not moved, and its caches are keyed on the storage. The first write to the
     * changed field is where anyone finds out, long after the save that caused it.
     *
     * The rebuild runs after the save and cannot fail it. The schema really was
     * saved; whether every tenant's table caught up is a separate fact, and the
     * report says which did not.
     */
    @Override
    protected Mono<Boolean> evictRecursively(String appCode, String clientCode, String name) {
        return super.evictRecursively(appCode, clientCode, name)
                .flatMap(evicted -> this.rebuild(appCode, clientCode, name).thenReturn(evicted));
    }

    /**
     * A draft save changes the draft definition, so the draft tables follow it.
     *
     * The same call covers both surfaces, and reconciling the live one here is not
     * wasted work that happens to be harmless: the live definition has not changed,
     * so its shape fingerprint has not moved and its plan comes out empty. Keying the
     * journal on the shape rather than a version is what makes "reconcile everything"
     * a cheap thing to say.
     */
    @Override
    protected Mono<Boolean> evictDraft(String appCode, String clientCode, String name) {
        return super.evictDraft(appCode, clientCode, name)
                .flatMap(evicted -> this.rebuild(appCode, clientCode, name).thenReturn(evicted));
    }

    /**
     * A deleted schema is as much a change of shape as an edited one, and worse: the
     * storages standing on it now resolve to nothing. They are rebuilt for the same
     * reason, and the resolver leaves an unresolvable field as a JSON column rather
     * than dropping it, so a delete does not quietly take a column with it.
     */
    @Override
    public Mono<Boolean> delete(String id) {
        return this.read(id)
                .flatMap(existing -> super.delete(id)
                        .flatMap(deleted -> this.rebuildDependents(existing).thenReturn(deleted)))
                .switchIfEmpty(Mono.defer(() -> super.delete(id)));
    }

    private Mono<CoreSchema> rebuildDependents(CoreSchema schema) {
        return this.rebuild(schema.getAppCode(), schema.getClientCode(), schema.getName())
                .thenReturn(schema);
    }

    private Mono<List<String>> rebuild(String appCode, String clientCode, String name) {
        return this.storageService
                .reconcileForSchemaChange(appCode, clientCode, name)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CoreSchemaService.rebuild"));
    }
}
