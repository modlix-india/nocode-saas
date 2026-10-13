package com.fincity.saas.commons.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.core.repository.EventDefinitionRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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

    /**
     * Every client's stored copy of this event in this app, unmerged: each override
     * holds only what it changes. No access check; callers decide who sees what.
     */
    public Flux<EventDefinition> readAllCopies(String name, String appCode) {
        return this.repo.findByNameAndAppCode(name, appCode);
    }

    /**
     * Turns one client's runs of a scheduled event on or off by writing ONLY
     * {@code schedule.enabled} into that client's own copy, creating the copy when
     * it has none. Cron, timezone, perClient and spread keep coming from the base,
     * so a later change to the base reaches this client too.
     *
     * No access check: this is for CoreServices.Event.SetScheduleEnabled, which
     * takes the app and the client from the caller's context itself.
     *
     * Emits true when the client's effective schedule changed, false when it was
     * already so, and empty when the app has no such event with a schedule.
     */
    public Mono<Boolean> setOwnScheduleEnabled(String name, String appCode, String clientCode, boolean enabled) {

        return FlatMapUtil.flatMapMono(

                        () -> this.readEffective(name, appCode, clientCode).filter(ed -> ed.getSchedule() != null),

                        effective -> {

                            // Absent means enabled, as the scheduler reads it.
                            boolean isEnabled = !Boolean.FALSE.equals(effective.getSchedule().getEnabled());
                            if (isEnabled == enabled)
                                return Mono.just(Boolean.FALSE);

                            return this.repo.findOneByNameAndAppCodeAndClientCode(name, appCode, clientCode)
                                    .map(own -> {
                                        EventSchedule schedule = own.getSchedule() == null ? new EventSchedule()
                                                : own.getSchedule();
                                        own.setSchedule(schedule.setEnabled(enabled));
                                        own.setVersion(own.getVersion() + 1);
                                        return own;
                                    })
                                    // A new copy overrides the definition the client sees now,
                                    // whose clientCode is the nearest one that holds it. The
                                    // override chain is followed through baseClientCode only:
                                    // without it the copy is read on its own, has no cron, and
                                    // the scheduler silently skips it as inactive.
                                    .defaultIfEmpty((EventDefinition) new EventDefinition()
                                            .setSchedule(new EventSchedule().setEnabled(enabled))
                                            .setBaseClientCode(effective.getClientCode()))
                                    .map(copy -> (EventDefinition) copy.setName(name)
                                            .setAppCode(appCode)
                                            .setClientCode(clientCode)
                                            .setMessage((enabled ? "Schedule enabled" : "Schedule disabled")
                                                    + " for " + clientCode + " by CoreServices.Event.SetScheduleEnabled"))
                                    .flatMap(this.repo::save)
                                    .flatMap(this::evictRecursively)
                                    .thenReturn(Boolean.TRUE);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventDefinitionService.setOwnScheduleEnabled"));
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
