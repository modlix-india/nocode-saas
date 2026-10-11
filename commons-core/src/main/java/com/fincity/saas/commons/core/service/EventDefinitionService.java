package com.fincity.saas.commons.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.repository.EventDefinitionRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EventDefinitionService extends AbstractOverridableDataService<EventDefinition, EventDefinitionRepository> {
    /** Draftable, like every other core object. See StorageService for why. */
    @Override
    protected boolean isDraftable() {
        return true;
    }


    protected EventDefinitionService() {
        super(EventDefinition.class);
    }

    /**
     * The definition as {@code clientCode} sees it: its own override merged over
     * every ancestor's along its inheritance chain, live surface only.
     *
     * For callers with no request to take a client from, the event scheduler
     * above all. It goes through the same per-client cache every other read
     * uses, and saves already evict that cache on every instance.
     */
    public Mono<EventDefinition> readEffective(String name, String appCode, String clientCode) {
        return this.readInternal(name, appCode, clientCode, clientCode, false)
                .map(ObjectWithUniqueID::getObject)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventDefinitionService.readEffective"));
    }

    @Override
    protected Mono<EventDefinition> updatableEntity(EventDefinition entity) {
        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setSchema(entity.getSchema());
                    existing.setSchedule(entity.getSchedule());

                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventDefinitionService.updatableEntity"));
    }
}
