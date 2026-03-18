package com.fincity.saas.entity.processor.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.SourceConfigDAO;
import com.fincity.saas.entity.processor.dto.SourceConfig;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class SourceConfigService implements IProcessorAccessService {

    private static final String CACHE_NAME_SOURCE_CONFIGS = "sourceConfigs";

    private static final String DEFAULT_CALL_SOURCE = "Social Media";
    private static final String DEFAULT_CALL_SUB_SOURCE = "Website Phone";
    private static final String DEFAULT_SOURCE = "Website Form";

    private final SourceConfigDAO sourceConfigDAO;
    private final CacheService cacheService;
    private final ProcessorMessageResourceService msgService;
    private final IFeignSecurityService securityService;

    private List<SourceConfig> defaultSources;

    public SourceConfigService(
            SourceConfigDAO sourceConfigDAO,
            CacheService cacheService,
            ProcessorMessageResourceService msgService,
            IFeignSecurityService securityService) {
        this.sourceConfigDAO = sourceConfigDAO;
        this.cacheService = cacheService;
        this.msgService = msgService;
        this.securityService = securityService;
    }

    @Override
    public ProcessorMessageResourceService getMsgService() {
        return this.msgService;
    }

    @Override
    public IFeignSecurityService getSecurityService() {
        return this.securityService;
    }

    public Mono<List<SourceConfig>> getAvailableSources(boolean onlyActive) {

        return FlatMapUtil.flatMapMono(

                this::hasAccess,

                access -> this.cacheService.cacheValueOrGet(
                        CACHE_NAME_SOURCE_CONFIGS,
                        () -> this.fetchSources(access, onlyActive),
                        access.getAppCode(), access.getEffectiveClientCode(), onlyActive))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SourceConfigService.getAvailableSources"));
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner'")   
    public Mono<List<SourceConfig>> saveAllSources(List<SourceConfig> sources) {

        return FlatMapUtil.flatMapMono(

                this::hasAccess,

                access -> {
                    if (!Objects.equals(access.getEffectiveClientCode(), access.getClientCode()))
                        return this.msgService.<List<SourceConfig>>throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                                access.getEffectiveClientCode());

                    return this.upsertTree(access, sources);
                },

                (access, saved) -> {
                    String ac = access.getAppCode();
                    String cc = access.getEffectiveClientCode();

                    return this.cacheService.put(CACHE_NAME_SOURCE_CONFIGS, saved, ac, cc, false)
                            .then(this.cacheService.evict(CACHE_NAME_SOURCE_CONFIGS, ac, cc, true))
                            .thenReturn(saved);
                })

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SourceConfigService.saveAllSources"));
    }

    public Mono<Tuple2<String, String>> getCallSource(String appCode, String clientCode) {

        return this.cacheService.cacheValueOrGet(
                        CACHE_NAME_SOURCE_CONFIGS,
                        () -> this.fetchSources(appCode, clientCode, true),
                        appCode, clientCode, true)
                .map(sources -> {
                    for (SourceConfig source : sources) {
                        for (SourceConfig child : source.getChildren()) {
                            if (child.isCallSource())
                                return Tuples.of(source.getName(), child.getName());
                        }
                    }
                    return Tuples.of(DEFAULT_CALL_SOURCE, DEFAULT_CALL_SUB_SOURCE);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SourceConfigService.getCallSource"));
    }

    private Mono<List<SourceConfig>> fetchSources(ProcessorAccess access, boolean onlyActive) {
        return this.fetchSources(access.getAppCode(), access.getEffectiveClientCode(), onlyActive);
    }

    private Mono<List<SourceConfig>> fetchSources(String appCode, String clientCode, boolean onlyActive) {

        Flux<SourceConfig> flux = onlyActive
                ? this.sourceConfigDAO.findActiveByAppAndClient(appCode, clientCode)
                : this.sourceConfigDAO.findAllByAppAndClient(appCode, clientCode);

        return flux.collectList()
                .flatMap(rows -> {
                    if (rows.isEmpty())
                        return Mono.just(this.defaultSources);
                    return Mono.just(buildTree(rows));
                });
    }

    private Mono<List<SourceConfig>> upsertTree(ProcessorAccess access, List<SourceConfig> sources) {

        String appCode = access.getAppCode();
        String clientCode = access.getEffectiveClientCode();
        ULong userId = access.getUserId();

        List<ULong> savedIds = new ArrayList<>();

        // First pass: upsert all parents and collect their IDs
        return Flux.fromIterable(sources)
                .concatMap(source -> {
                    source.setAppCode(appCode);
                    source.setClientCode(clientCode);
                    source.setParentId(null);
                    return this.upsertOne(source, userId);
                })
                .collectList()
                .flatMap(savedParents -> {

                    savedParents.forEach(p -> savedIds.add(p.getId()));

                    // Second pass: upsert all children using saved parent IDs
                    return Flux.fromIterable(savedParents)
                            .concatMap(savedParent -> {
                                SourceConfig original = sources.stream()
                                        .filter(s -> s.getId() != null && s.getId().equals(savedParent.getId())
                                                || s.getName().equals(savedParent.getName()))
                                        .findFirst()
                                        .orElse(savedParent);

                                if (original.getChildren() == null || original.getChildren().isEmpty()) {
                                    savedParent.setChildren(List.of());
                                    return Mono.just(savedParent);
                                }

                                return Flux.fromIterable(original.getChildren())
                                        .concatMap(child -> {
                                            child.setAppCode(appCode);
                                            child.setClientCode(clientCode);
                                            child.setParentId(savedParent.getId());
                                            return this.upsertOne(child, userId)
                                                    .doOnNext(saved -> savedIds.add(saved.getId()));
                                        })
                                        .collectList()
                                        .map(savedChildren -> {
                                            savedParent.setChildren(savedChildren);
                                            return savedParent;
                                        });
                            })
                            .collectList();
                })
                .flatMap(tree -> this.sourceConfigDAO
                        .deleteByAppAndClientExcludingIds(appCode, clientCode, savedIds)
                        .thenReturn(tree));
    }

    private Mono<SourceConfig> upsertOne(SourceConfig config, ULong userId) {
        if (config.getId() != null) {
            config.setUpdatedBy(userId);
            return this.sourceConfigDAO.update(config);
        }
        config.setCreatedBy(userId);
        return this.sourceConfigDAO.insert(config);
    }

    private static List<SourceConfig> buildTree(List<SourceConfig> flatRows) {

        Map<ULong, SourceConfig> byId = new LinkedHashMap<>();
        List<SourceConfig> roots = new ArrayList<>();

        for (SourceConfig row : flatRows) {
            row.setChildren(new ArrayList<>());
            byId.put(row.getId(), row);
        }

        for (SourceConfig row : flatRows) {
            if (row.getParentId() == null) {
                roots.add(row);
            } else {
                SourceConfig parent = byId.get(row.getParentId());
                if (parent != null)
                    parent.getChildren().add(row);
            }
        }

        return roots;
    }

    @PostConstruct
    private void init() {

        Map<String, List<String>> defaultSourceMap = new LinkedHashMap<>();
        defaultSourceMap.put(DEFAULT_CALL_SOURCE, List.of(
                "Facebook", "Instagram", "Google PPC", "Facebook Forms",
                "Google Lead Forms", DEFAULT_SOURCE, "LinkedIn Forms", "Google", DEFAULT_CALL_SUB_SOURCE));
        defaultSourceMap.put("Channel Partner", List.of("Others"));
        defaultSourceMap.put("Below the Line", List.of("Hoardings"));
        defaultSourceMap.put("Walk-In", List.of(
                "Channel Partner", "Newspaper", "Hoardings", "Web", "Email", "SMS",
                "Exhibition", "Event", "Referral", "Just Walked by", "Society", "Corporate", "Radio"));

        List<SourceConfig> sources = new ArrayList<>();
        int sourceOrder = 0;

        for (Map.Entry<String, List<String>> entry : defaultSourceMap.entrySet()) {

            SourceConfig source = new SourceConfig();
            source.setName(entry.getKey());
            source.setDisplayOrder(sourceOrder++);
            source.setActive(true);

            List<SourceConfig> children = new ArrayList<>();
            int childOrder = 0;

            for (String subSourceName : entry.getValue()) {

                SourceConfig child = new SourceConfig();
                child.setName(subSourceName);
                child.setDisplayOrder(childOrder++);
                child.setActive(true);
                child.setCallSource(DEFAULT_CALL_SUB_SOURCE.equals(subSourceName)
                        && DEFAULT_CALL_SOURCE.equals(entry.getKey()));
                child.setDefaultSource(DEFAULT_SOURCE.equals(subSourceName)
                        && DEFAULT_CALL_SOURCE.equals(entry.getKey()));

                children.add(child);
            }

            source.setChildren(children);
            sources.add(source);
        }

        this.defaultSources = List.copyOf(sources);
    }
}
