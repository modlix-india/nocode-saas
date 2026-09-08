package com.fincity.saas.message.enums.dispatch;

/**
 * Which family of provider events a handoff belongs to.
 *
 * <p>Part of the outbox's unique key alongside the event id, because two providers' id spaces are
 * unrelated: an Exotel call Sid and a Meta message id could in principle collide, and without the
 * channel one of them would be silently swallowed as a duplicate.
 */
public enum DispatchChannel {
    WHATSAPP,
    CALL
}
