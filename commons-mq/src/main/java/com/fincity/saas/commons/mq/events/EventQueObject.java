package com.fincity.saas.commons.mq.events;

import java.io.Serializable;
import java.util.Map;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EventQueObject implements Serializable {

	private static final long serialVersionUID = -2382306278225358489L;

	private String eventName;
	private String clientCode;
	private String appCode;
	private String xDebug;

	/**
	 * Whether the request that raised this event was on the app's draft surface.
	 *
	 * Carried on the message rather than read from a header, for the same reason
	 * xDebug is: the listener runs on its own thread with no inbound request, so
	 * the only way the marker survives the hop is if it travels in the body.
	 *
	 * Without it, an event raised by a draft-surface write executed with
	 * isDraft() == false, and a CALL_CORE_FUNCTION action writing through
	 * CoreServices.Storage landed in the LIVE database. The draft surface's own
	 * data isolation was defeated by its own event pipeline, silently.
	 */
	private boolean draft;

	private Map<String, Object> data; // NOSONAR
	private ContextAuthentication authentication;
}
