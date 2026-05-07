package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.SourceDAO;
import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class SourceService implements IProcessorAccessService {

    private final SourceDAO sourceDAO;
    private final ProcessorMessageResourceService msgService;
    private final IFeignSecurityService securityService;

    private List<Source> defaultSources;

    public SourceService(
            SourceDAO sourceDAO, ProcessorMessageResourceService msgService, IFeignSecurityService securityService) {
        this.sourceDAO = sourceDAO;
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

    public Mono<List<Source>> getAvailableSources(boolean onlyActive) {

        return this.hasAccess()
                .flatMap(access -> this.fetchSources(access.getAppCode(), access.getEffectiveClientCode(), onlyActive))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SourceService.getAvailableSources"));
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<List<Source>> saveAllSources(List<Source> sources) {

        return FlatMapUtil.flatMapMono(this::hasAccess, access -> {
                    if (!Objects.equals(access.getEffectiveClientCode(), access.getClientCode()))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                                access.getEffectiveClientCode());

                    return this.upsertTree(access, sources);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SourceService.saveAllSources"));
    }

    private Mono<List<Source>> fetchSources(String appCode, String clientCode, boolean onlyActive) {

        Flux<Source> flux = onlyActive
                ? this.sourceDAO.findActiveByAppAndClient(appCode, clientCode)
                : this.sourceDAO.findAllByAppAndClient(appCode, clientCode);

        return flux.collectList().map(rows -> rows.isEmpty() ? this.defaultSources : buildTree(rows));
    }

    private Mono<List<Source>> upsertTree(ProcessorAccess access, List<Source> sources) {

        String appCode = access.getAppCode();
        String clientCode = access.getEffectiveClientCode();
        ULong userId = access.getUserId();

        Set<ULong> savedIds = new HashSet<>();

        return Flux.fromIterable(sources)
                .concatMap(parentInput -> {
                    parentInput.setAppCode(appCode);
                    parentInput.setClientCode(clientCode);
                    parentInput.setParentId(null);

                    return this.upsertOne(parentInput, userId).flatMap(savedParent -> {
                        savedIds.add(savedParent.getId());

                        List<Source> kids = parentInput.getChildren();
                        if (kids == null || kids.isEmpty()) {
                            savedParent.setChildren(List.of());
                            return Mono.just(savedParent);
                        }

                        return Flux.fromIterable(kids)
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
                    });
                })
                .collectList()
                .flatMap(tree -> this.sourceDAO
                        .deleteByAppAndClientExcludingIds(appCode, clientCode, savedIds)
                        .thenReturn(tree));
    }

    private Mono<Source> upsertOne(Source source, ULong userId) {
        if (source.getId() != null) {
            source.setUpdatedBy(userId);
            return this.sourceDAO.update(source);
        }
        source.setCreatedBy(userId);
        return this.sourceDAO.insert(source);
    }

    private static List<Source> buildTree(List<Source> rows) {

        Map<ULong, Source> byId = new LinkedHashMap<>();
        List<Source> roots = new ArrayList<>();

        for (Source row : rows) {
            row.setChildren(new ArrayList<>());
            byId.put(row.getId(), row);
        }

        for (Source row : rows) {
            if (row.getParentId() == null) {
                roots.add(row);
            } else {
                Source parent = byId.get(row.getParentId());
                if (parent != null) parent.getChildren().add(row);
            }
        }

        return roots;
    }

    @PostConstruct
    private void init() {

        Map<String, List<String>> defaultSourceMap = new LinkedHashMap<>();
        defaultSourceMap.put(
                "Social Media",
                List.of(
                        "Facebook",
                        "Instagram",
                        "Google PPC",
                        "Facebook Forms",
                        "Google Lead Forms",
                        "Website Form",
                        "LinkedIn Forms",
                        "Google",
                        "Website Phone"));
        defaultSourceMap.put("Channel Partner", List.of("Others"));
        defaultSourceMap.put("Below the Line", List.of("Hoardings"));
        defaultSourceMap.put(
                "Walk-In",
                List.of(
                        "Channel Partner",
                        "Newspaper",
                        "Hoardings",
                        "Web",
                        "Email",
                        "SMS",
                        "Exhibition",
                        "Event",
                        "Referral",
                        "Just Walked by",
                        "Society",
                        "Corporate",
                        "Radio"));

        List<Source> seeded = new ArrayList<>();
        int sourceOrder = 0;

        for (Map.Entry<String, List<String>> entry : defaultSourceMap.entrySet()) {

            Source parent = new Source();
            parent.setName(entry.getKey());
            parent.setDisplayOrder(sourceOrder++);
            parent.setActive(true);

            List<Source> children = new ArrayList<>();
            int childOrder = 0;

            for (String subSourceName : entry.getValue()) {

                Source child = new Source();
                child.setName(subSourceName);
                child.setDisplayOrder(childOrder++);
                child.setActive(true);

                children.add(child);
            }

            parent.setChildren(children);
            seeded.add(parent);
        }

        this.defaultSources = List.copyOf(seeded);
    }
}
