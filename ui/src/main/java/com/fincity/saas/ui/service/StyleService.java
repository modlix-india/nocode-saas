package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.ui.document.StyleTheme;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.document.Style;
import com.fincity.saas.ui.repository.StyleRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class StyleService extends AbstractUIOverridableDataService<Style, StyleRepository> {

    protected StyleService() {
        super(Style.class);
    }

    @Override
    protected Mono<Style> updatableEntity(Style entity) {

        return flatMapMono(

                () -> this.read(entity.getId()),

                existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setStyleString(entity.getStyleString());
                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "StyleService.updatableEntity"));
    }

    @Override
    public String getObjectName() {
        return "Style";
    }

    @Override
    public Mono<Style> update(Style style) {
        return super.update(style)
                .flatMap(this.cacheService.evictAllFunction(EngineService.CACHE_NAME_STYLE + "-" + style.getAppCode()));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(id),

                style -> super.delete(id),

                (style, deleted) -> this.cacheService.evictAll(EngineService.CACHE_NAME_STYLE + "-" + style.getAppCode())
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "StyleService.delete"));
    }
}
