package com.fincity.saas.message.service.dispatch.handler;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Calls owned by core, raised as {@code CALL_EVENT}.
 *
 * <p>A call is owned by core when it was placed with {@code ownerService=core}, which is what
 * {@code CoreServices.Message.MakeCall} does, or when the Exotel connection it was placed or received
 * on names {@code ownerService: core} in its {@code connectionDetails}. {@code numberId} is always
 * null: Exotel numbers live in the tenant's own connection and this service has no table of them, so
 * {@code callerId} is what identifies the line.
 *
 * <p>An inbound call takes its owner from that connection first, then from the service answering the
 * connect applet; with neither, it has no owner and its events park (see {@code
 * ExotelCallService.inboundOwner}).
 */
@Component
public class CoreCallHandler extends CoreEventHandler {

    public static final String CORE_EVENT_NAME = "CALL_EVENT";

    public CoreCallHandler(EventCreationService eventCreationService) {
        super(eventCreationService);
    }

    @Override
    public DispatchChannel getChannel() {
        return DispatchChannel.CALL;
    }

    @Override
    public String getCoreEventName() {
        return CORE_EVENT_NAME;
    }

    @Override
    protected String getDefaultEventType() {
        return DispatchEventType.CALL_STATUS.name();
    }

    @Override
    protected Object getNumberId(Map<String, Object> payload) {
        return null;
    }
}
