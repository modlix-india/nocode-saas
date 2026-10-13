package com.fincity.saas.core.mq;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fincity.saas.commons.core.service.EventRunService;
import com.fincity.saas.commons.mq.events.EventQueObject;

/**
 * What to say when an event reaches core and the app and client it was raised for have no
 * EventAction of that name.
 *
 * <p>Core drops such an event, and used to do so in silence. For most events that is correct: an
 * app need not react to every user or storage event. For the events another service hands to core
 * so an app can take over a channel, it is data loss. The message service raises {@code
 * WHATSAPP_INBOUND} for every message on a number whose owner is {@code core}, and {@code
 * CALL_EVENT} for every call it owns; with no EventAction they vanish, and nothing anywhere said
 * which app or client was missing it.
 *
 * <p>So those hand-off events are warned about every time. Any other event is warned about once per
 * event, app and client every {@link #REPEAT_AFTER_MILLIS}, so the log names the gap without being
 * flooded by an app that simply does not care about an event.
 */
final class UnhandledEventLog {

    /** Events raised so an app can own a channel: losing one loses a customer's message or call. */
    static final Set<String> HANDOFF_EVENTS = EventRunService.HANDOFF_EVENTS;

    static final long REPEAT_AFTER_MILLIS = 60L * 60L * 1000L;

    /** Bound on the remembered keys, so an unbounded set of event names cannot grow the map. */
    static final int MAX_REMEMBERED = 10_000;

    private final Map<String, Long> lastWarned = new ConcurrentHashMap<>();

    /** The warning to log for this event at {@code now}, or null when it was warned about recently. */
    String warning(EventQueObject event, long now) {

        if (event == null)
            return null;

        String name = event.getEventName();
        boolean handoff = name != null && HANDOFF_EVENTS.contains(name);

        if (!handoff) {
            String key = name + "|" + event.getAppCode() + "|" + event.getClientCode();
            Long last = this.lastWarned.get(key);
            if (last != null && now - last < REPEAT_AFTER_MILLIS)
                return null;
            if (this.lastWarned.size() >= MAX_REMEMBERED)
                this.lastWarned.clear();
            this.lastWarned.put(key, now);
        }

        StringBuilder sb = new StringBuilder()
                .append("No EventAction '")
                .append(name)
                .append("' for app '")
                .append(event.getAppCode())
                .append("' client '")
                .append(event.getClientCode())
                .append("': the event is dropped.");

        if (handoff)
            sb.append(" The message service handed this to core because the number or call is owned by core;")
                    .append(" define an EventAction named ")
                    .append(name)
                    .append(" in app '")
                    .append(event.getAppCode())
                    .append("' (CALL_CORE_FUNCTION to a function that stores it), or every such event is lost.");
        else
            sb.append(" Not repeated for this event, app and client for an hour.");

        return sb.toString();
    }
}
