package com.fincity.saas.commons.mongo.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.mongo.model.ListResultObject;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;

import reactor.core.publisher.Mono;

public class AbstractOverridableDataController<D extends AbstractOverridableDTO<D>, R extends IOverridableDataRepository<D>, S extends AbstractOverridableDataService<D, R>>
        extends AbstractMongoUpdatableDataController<String, D, R, S> {

    @Override
    @GetMapping("/nomap2")
    public Mono<ResponseEntity<Page<D>>> readPageFilter(Pageable pageable,
            ServerHttpRequest request) {
        return Mono.just(ResponseEntity.badRequest()
                .build());
    }

    @Override
    @PostMapping("/nomap2")
    public Mono<ResponseEntity<Page<D>>> readPageFilter(Query query) {
        return Mono.just(ResponseEntity.badRequest()
                .build());
    }

    @GetMapping()
    public Mono<ResponseEntity<Page<ListResultObject<D>>>> readPageFilterLRO(
            @RequestParam(required = false, defaultValue = "false") boolean eager,
            @RequestParam(required = false, defaultValue = "false") boolean clientOnly,
            Pageable pageable,
            ServerHttpRequest request) {
        final Pageable finPageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID)
                : pageable);
        return this.service.readPageFilterLRO(eager, clientOnly, finPageable, request.getQueryParams())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/createForClient/{id}/{clientCode}")
    public Mono<ResponseEntity<D>> createForClient(@PathVariable String id, @PathVariable String clientCode) {

        return this.service.createForClient(id, clientCode)
                .map(ResponseEntity::ok);
    }

    // ── Draft and publish ──────────────────────────────────────────
    //
    // The write target is explicit on the call, never inferred from ambient
    // request state, so a page running on the draft surface doing an ordinary
    // SendData PUT is not silently diverted into a draft it never asked for.
    //
    // `draft` is safe as a query parameter on these routes: only readPageFilterLRO
    // turns unrecognised parameters into Mongo regex conditions, via
    // paramToConditionLRO. PUT /{id} and GET /{id} do not.

    /**
     * PUT /{id} writes live exactly as before. PUT /{id}?draft=true stores the
     * body as unpublished work and does not touch the live document at all.
     */
    @PutMapping(value = AbstractMongoDataController.PATH_ID, params = "draft")
    public Mono<ResponseEntity<Draft>> putDraft(@PathVariable(name = PATH_VARIABLE_ID) final String id,
            @RequestParam(name = "draft") boolean draft, @RequestBody D entity) {

        if (!draft)
            return Mono.just(ResponseEntity.badRequest().build());

        entity.setId(id);
        return this.service.saveDraft(entity).map(ResponseEntity::ok);
    }

    /**
     * POST creates live and published, exactly as before. POST ?draft=true creates
     * a real live document marked never-published: addressable by id and present in
     * the builder, invisible on the live surface until first publish.
     */
    @PostMapping(params = "draft")
    public Mono<ResponseEntity<D>> createDraft(@RequestParam(name = "draft") boolean draft,
            @RequestBody D entity) {

        if (!draft)
            return this.service.create(entity).map(ResponseEntity::ok);

        return this.service.createUnpublished(entity).map(ResponseEntity::ok);
    }

    /**
     * The object as an editor should see it: the draft when one exists, otherwise
     * the live document. Runs the same access check as a live read.
     */
    @GetMapping(value = AbstractMongoDataController.PATH_ID, params = "draft")
    public Mono<ResponseEntity<D>> readDraft(@PathVariable(name = PATH_VARIABLE_ID) final String id,
            @RequestParam(name = "draft") boolean draft) {

        if (!draft)
            return this.service.read(id).map(ResponseEntity::ok);

        return this.service.readDraft(id).map(ResponseEntity::ok);
    }

    @PostMapping("/{id}/publish")
    public Mono<ResponseEntity<D>> publish(@PathVariable String id,
            @RequestParam(required = false) String message) {

        return this.service.publish(id, message).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}/draft")
    public Mono<ResponseEntity<Boolean>> discardDraft(@PathVariable String id) {

        return this.service.discardDraft(id).map(ResponseEntity::ok);
    }
}
