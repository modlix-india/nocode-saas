package com.fincity.saas.commons.core.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.dao.RemoteRepositoryDAO;
import com.fincity.saas.commons.core.dto.RemoteRepository;
import com.fincity.saas.commons.core.feign.IFeignEntityProcessor;
import com.fincity.saas.commons.core.jooq.enums.CoreRemoteRepositoriesRepoName;
import com.fincity.saas.commons.core.jooq.tables.records.CoreRemoteRepositoriesRecord;
import com.fincity.saas.commons.core.kirun.repository.entityprocessor.EPRemoteFunctionRepository;
import com.fincity.saas.commons.core.kirun.repository.entityprocessor.EPRemoteSchemaRepository;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class RemoteRepositoryService
        extends
        AbstractJOOQUpdatableDataService<CoreRemoteRepositoriesRecord, ULong, RemoteRepository, RemoteRepositoryDAO> {

    private static final String CACHE_RR_NAME = "cacheRemoteRepositories";

    private final CacheService cacheService;
    private final CoreMessageResourceService messageService;
    private final Gson gson;
    private final Map<String, Optional<Tuple2<ReactiveRepository<ReactiveFunction>, ReactiveRepository<Schema>>>> functionRepositories = new ConcurrentHashMap<>();

    private final IFeignEntityProcessor feignEntityProcessor;

    public RemoteRepositoryService(CacheService cacheService, CoreMessageResourceService messageService, Gson gson,
            IFeignEntityProcessor feignEntityProcessor) {
        this.cacheService = cacheService;
        this.messageService = messageService;
        this.gson = gson;
        this.feignEntityProcessor = feignEntityProcessor;
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser()
                .map(user -> ULong.valueOf(user.getId()))
                .defaultIfEmpty(null);
    }

    @Override
    protected Mono<RemoteRepository> updatableEntity(RemoteRepository entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(entity.getId()),
                existing -> {
                    if (entity.getAppCode() != null) {
                        existing.setAppCode(entity.getAppCode());
                    }
                    if (entity.getRepoName() != null) {
                        existing.setRepoName(entity.getRepoName());
                    }
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RemoteRepositoryService.updatableEntity"));
    }

    /**
     * Find a remote repository by app code and repository name.
     *
     * @param appCode  The application code
     * @param repoName The repository name
     * @return Mono containing the RemoteRepository if found, empty otherwise
     */
    public Mono<List<CoreRemoteRepositoriesRepoName>> getRemoteRepositoryNames(String appCode) {
        return this.cacheService.cacheValueOrGet(CACHE_RR_NAME, () -> this.dao.findByAppCode(appCode), appCode);
    }

    /**
     * Create a remote repository.
     *
     * @param entity The remote repository to create
     * @return Mono containing the created RemoteRepository
     */
    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    public Mono<RemoteRepository> create(RemoteRepository entity) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> super.create(entity))
                .flatMap(this.cacheService.evictFunction(CACHE_RR_NAME, entity.getAppCode()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RemoteRepositoryService.create"));
    }

    /**
     * Read a remote repository by ID.
     *
     * @param id The ID of the remote repository
     * @return Mono containing the RemoteRepository if found
     */
    @PreAuthorize("hasAuthority('Authorities.Application_READ')")
    public Mono<RemoteRepository> read(ULong id) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> super.read(id))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RemoteRepositoryService.read"));
    }

    /**
     * Update a remote repository.
     *
     * @param entity The remote repository to update
     * @return Mono containing the updated RemoteRepository
     */
    @PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
    public Mono<RemoteRepository> update(RemoteRepository entity) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> super.update(entity))
                .flatMap(this.cacheService.evictFunction(CACHE_RR_NAME, entity.getAppCode()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RemoteRepositoryService.update"));
    }

    /**
     * Delete a remote repository by ID.
     *
     * @param id The ID of the remote repository to delete
     * @return Mono containing the number of deleted records
     */
    @PreAuthorize("hasAuthority('Authorities.Application_DELETE')")
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> super.delete(id))
                .flatMap(this.cacheService.evictAllFunction(CACHE_RR_NAME))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RemoteRepositoryService.delete"));
    }

    public Mono<Optional<Tuple2<ReactiveRepository<ReactiveFunction>, ReactiveRepository<Schema>>>> getRemoteRepositories(
            String appCode, String clientCode) {

        String cacheKey = appCode + " - " + clientCode;
        if (this.functionRepositories.containsKey(cacheKey)) {
            return Mono.just(this.functionRepositories.get(cacheKey));
        }

        return FlatMapUtil.flatMapMono(

                () -> this.getRemoteRepositoryNames(appCode),

                rrNames -> {

                    Optional<Tuple2<ReactiveRepository<ReactiveFunction>, ReactiveRepository<Schema>>> op;
                    if (rrNames.isEmpty()) {
                        this.functionRepositories.put(cacheKey, Optional.empty());
                        op = Optional
                                .<Tuple2<ReactiveRepository<ReactiveFunction>, ReactiveRepository<Schema>>>empty();
                    } else {
                        op = Optional.of(Tuples.of(
                                new EPRemoteFunctionRepository(this.feignEntityProcessor, appCode, clientCode,
                                        this.gson, this.messageService),
                                new EPRemoteSchemaRepository(this.feignEntityProcessor, appCode, clientCode,
                                        this.gson)));
                    }

                    return Mono.just(op);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "RemoteRepositoryService.getRemoteRepositories"));
    }
}
