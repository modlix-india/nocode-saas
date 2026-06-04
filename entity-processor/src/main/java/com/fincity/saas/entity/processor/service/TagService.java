package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.TagDAO;
import com.fincity.saas.entity.processor.dto.Tag;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class TagService implements IProcessorAccessService {

    private final TagDAO tagDAO;
    private final ProcessorMessageResourceService msgService;
    private final IFeignSecurityService securityService;

    private List<Tag> defaultTags;

    public TagService(
            TagDAO tagDAO, ProcessorMessageResourceService msgService, IFeignSecurityService securityService) {
        this.tagDAO = tagDAO;
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

    public Mono<List<Tag>> getAvailableTags(boolean onlyActive) {
        return this.hasAccess()
                .flatMap(access -> this.fetchTags(access.getAppCode(), access.getEffectiveClientCode(), onlyActive))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TagService.getAvailableTags"));
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<List<Tag>> saveAllTags(List<Tag> tags) {
        return FlatMapUtil.flatMapMono(this::hasAccess, access -> {
                    if (!Objects.equals(access.getEffectiveClientCode(), access.getClientCode()))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.FORBIDDEN_APP_ACCESS,
                                access.getEffectiveClientCode());

                    return this.upsertTags(access, tags);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TagService.saveAllTags"));
    }

    private Mono<List<Tag>> fetchTags(String appCode, String clientCode, boolean onlyActive) {
        Flux<Tag> flux = onlyActive
                ? this.tagDAO.findActiveByAppAndClient(appCode, clientCode)
                : this.tagDAO.findAllByAppAndClient(appCode, clientCode);

        return flux.collectList().map(rows -> rows.isEmpty() ? this.defaultTags : rows);
    }

    private Mono<List<Tag>> upsertTags(ProcessorAccess access, List<Tag> tags) {
        String appCode = access.getAppCode();
        String clientCode = access.getEffectiveClientCode();
        ULong userId = access.getUserId();

        Set<ULong> savedIds = new HashSet<>();

        return Flux.fromIterable(tags)
                .concatMap(tagInput -> {
                    tagInput.setAppCode(appCode);
                    tagInput.setClientCode(clientCode);

                    return this.upsertOne(tagInput, userId).doOnNext(saved -> savedIds.add(saved.getId()));
                })
                .collectList()
                .flatMap(list -> this.tagDAO
                        .deleteByAppAndClientExcludingIds(appCode, clientCode, savedIds)
                        .thenReturn(list));
    }

    private Mono<Tag> upsertOne(Tag tag, ULong userId) {
        if (tag.getId() != null) {
            tag.setUpdatedBy(userId);
            return this.tagDAO.update(tag);
        }
        tag.setCreatedBy(userId);
        return this.tagDAO.insert(tag);
    }

    @PostConstruct
    private void init() {
        List<String> defaultNames = List.of("HOT", "WARM", "COLD");
        List<Tag> seeded = new ArrayList<>();
        for (String name : defaultNames) {
            Tag tag = new Tag();
            tag.setName(name);
            tag.setActive(true);
            seeded.add(tag);
        }
        this.defaultTags = List.copyOf(seeded);
    }
}
