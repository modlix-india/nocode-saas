package com.fincity.saas.ui.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.document.StyleTheme;
import com.fincity.saas.ui.repository.StyleThemeRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class StyleThemeService extends AbstractUIOverridableDataService<StyleTheme, StyleThemeRepository> {

    public StyleThemeService() {
        super(StyleTheme.class);
    }

    /**
     * The style cache is evicted alongside the theme cache because the served CSS
     * now depends on which theme is active: a theme may carry its own style
     * document, and deleting a theme shifts resolution to the next one, which may
     * carry a different one. Evicting only the theme cache left `/api/ui/style`
     * serving the departed theme's stylesheet.
     */
    @Override
    public Mono<StyleTheme> update(StyleTheme styleTheme) {
        return super.update(styleTheme)
                .flatMap(this.cacheService.evictAllFunction(EngineService.CACHE_NAME_THEME + "-" + styleTheme.getAppCode()))
                .flatMap(this.cacheService.evictAllFunction(EngineService.CACHE_NAME_STYLE + "-" + styleTheme.getAppCode()));
    }

    /** Both OUI caches serve both surfaces, for the same reason as update() above. */
    @Override
    protected Mono<Boolean> evictDraft(String appCode, String clientCode, String name) {
        return super.evictDraft(appCode, clientCode, name)
                .flatMap(this.cacheService.evictAllFunction(EngineService.CACHE_NAME_THEME + "-" + appCode))
                .flatMap(this.cacheService.evictAllFunction(EngineService.CACHE_NAME_STYLE + "-" + appCode));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(id),

                thm -> super.delete(id),

                (thm, deleted) -> this.cacheService.evictAll(EngineService.CACHE_NAME_THEME + "-" + thm.getAppCode()),

                (thm, deleted, cacheEvicted) -> this.cacheService
                        .evictAll(EngineService.CACHE_NAME_STYLE + "-" + thm.getAppCode()),

                (thm, deleted, cacheEvicted, styleEvicted) -> this.ssrCacheEvictionService
                        .evictByAppCode(thm.getAppCode())
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "StyleThemeService.delete"));
    }

    @Override
    protected Mono<StyleTheme> updatableEntity(StyleTheme entity) {

        return flatMapMono(

                () -> this.read(entity.getId()),

                existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setVariables(entity.getVariables());

                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "StyleThemeService.updatableEntity"));
    }

    @Override
    public String getObjectName() {
        return "Theme";
    }
}
