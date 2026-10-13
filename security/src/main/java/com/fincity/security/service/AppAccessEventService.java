package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.jooq.tables.records.SecurityAppAccessRecord;

import reactor.core.publisher.Mono;

/**
 * Raises {@value #APP_ACCESS_CHANGED} whenever a client's access to an app changes, so an app can
 * react to it in an EventAction: start a client's schedules when it gets edit access, clean up when
 * it loses it, and so on. What to do is the app's decision; this only says what happened.
 *
 * <p>The event is raised for the app whose access changed and for the client whose access changed,
 * so core resolves the app's own EventAction (a client without one inherits the app owner's) and
 * runs its tasks in that client's context.
 *
 * <p>Event data, every value a string so a template never sees a null:
 * <ul>
 * <li>{@code action}: {@link Action}, what the change was.</li>
 * <li>{@code previous}, {@code current}: {@link Level} before and after.</li>
 * <li>{@code appCode}, {@code appId}, {@code clientCode}, {@code clientId},
 * {@code clientLevelType}, {@code clientTypeCode}.</li>
 * <li>{@code changedBy}: the id of the user who made the change, "" when none is logged in.</li>
 * </ul>
 *
 * <p>Raising never fails the access change it reports: the write has already happened, so a
 * publish failure is logged and swallowed.
 */
@Service
public class AppAccessEventService {

    public static final String APP_ACCESS_CHANGED = "APP_ACCESS_CHANGED";

    private static final Logger logger = LoggerFactory.getLogger(AppAccessEventService.class);

    /** A client's access to an app. */
    public enum Level {
        NONE, READ, EDIT;

        public static Level of(boolean writeAccess) {
            return writeAccess ? EDIT : READ;
        }

        public static Level of(SecurityAppAccessRecord row) {
            if (row == null)
                return NONE;
            return row.getEditAccess() != null && row.getEditAccess().intValue() == 1 ? EDIT : READ;
        }
    }

    /** What a change from one {@link Level} to another was. */
    public enum Action {
        READ_ADDED, EDIT_ADDED, UPGRADED_TO_EDIT, DOWNGRADED_TO_READ, REMOVED;

        /** Null when nothing changed, which raises no event. */
        public static Action of(Level previous, Level current) {

            if (previous == current)
                return null;

            if (current == Level.NONE)
                return REMOVED;

            if (previous == Level.NONE)
                return current == Level.EDIT ? EDIT_ADDED : READ_ADDED;

            return current == Level.EDIT ? UPGRADED_TO_EDIT : DOWNGRADED_TO_READ;
        }
    }

    private final ClientService clientService;
    private final EventCreationService ecService;

    public AppAccessEventService(ClientService clientService, EventCreationService ecService) {
        this.clientService = clientService;
        this.ecService = ecService;
    }

    /**
     * Raises the event for one client's access to one app. Emits true when an event was published,
     * false when nothing changed or the publish failed.
     */
    public Mono<Boolean> raise(String appCode, ULong appId, ULong clientId, Level previous, Level current) {

        Action action = Action.of(previous, current);

        if (action == null || appCode == null || clientId == null)
            return Mono.just(Boolean.FALSE);

        return this.clientService.getClientTypeNCodeNClientLevel(clientId)
                .flatMap(client -> SecurityContextUtil.getUsersContextUser()
                        .map(user -> String.valueOf(user.getId()))
                        .defaultIfEmpty("")
                        .flatMap(changedBy -> {

                            Map<String, Object> data = new HashMap<>();
                            data.put("action", action.name());
                            data.put("previous", previous.name());
                            data.put("current", current.name());
                            data.put("appCode", appCode);
                            data.put("appId", appId == null ? "" : appId.toString());
                            data.put("clientCode", client.getT2());
                            data.put("clientId", clientId.toString());
                            data.put("clientTypeCode", client.getT1());
                            data.put("clientLevelType", client.getT3());
                            data.put("changedBy", changedBy);

                            return this.ecService.createEvent(new EventQueObject()
                                    .setAppCode(appCode)
                                    .setClientCode(client.getT2())
                                    .setEventName(APP_ACCESS_CHANGED)
                                    .setData(data));
                        }))
                .onErrorResume(e -> {
                    logger.error("Could not raise {} for app {} and client {}", APP_ACCESS_CHANGED, appCode,
                            clientId, e);
                    return Mono.just(Boolean.FALSE);
                })
                .defaultIfEmpty(Boolean.FALSE);
    }
}
