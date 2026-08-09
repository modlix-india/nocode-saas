package com.fincity.saas.entity.processor.model.message;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What travels down a browser's WhatsApp event stream, and across Redis to get there.
 *
 * <h2>This is delivered to entitled users only, and that is why it may carry a name</h2>
 *
 * <p>The first version of this class was broadcast to every logged-in browser in a tenant and
 * therefore carried nothing but a deal id: each browser then fetched the ticket through the normal
 * read, which is what enforced access. That worked and did not scale. Twenty people online with a
 * handful of tabs each turned one inbound message into hundreds of ticket reads, every one of them
 * running the full {@code hasAccess()} path with its security calls, and all but a few thrown away
 * as 403s.
 *
 * <p>So the fan-out now applies the access rule itself, in
 * {@code WhatsappEventService.entitled(...)}, and the fields below exist to let it: the deal's
 * {@code clientId}, {@code assignedUserId} and {@code createdBy} are the three columns the DAO's own
 * filter tests. Because only an entitled browser receives the event, the display fields can ride
 * along and the client fetch disappears entirely.
 *
 * <p><b>The consequence, stated plainly.</b> A lead's name is now on a Redis channel shared by every
 * instance and every tenant, and the only thing standing between it and the wrong browser is that
 * filter. Previously the guard was the ticket read, which is the same code paying customers rely on
 * everywhere else; now it is a second implementation of the same rule. That is a real reduction in
 * safety margin bought for a real reduction in load. It is defensible only while
 * {@code WhatsappEventServiceTest} keeps pinning the filter to
 * {@code BaseProcessorDAO.addUserIds}'s behaviour, so if that rule changes and the test is deleted
 * rather than fixed, this class should go back to carrying an id alone.
 *
 * <p><b>It still carries no message body and no phone number.</b> Those buy nothing: the client has
 * to refetch the thread anyway to render it in order.
 *
 * <h2>It carries no authority</h2>
 *
 * <p>Redis pub/sub is fire and forget, so an instance briefly disconnected loses the event. The
 * message row is already durable in {@code entity_processor_whatsapp_messages}; a lost event costs a
 * refresh, never a message. Nothing may be built here that only the stream knows.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WhatsappStreamEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 6072317094551024301L;

    /** A new message row, inbound or an outbound mirror. */
    public static final String KIND_MESSAGE = "MESSAGE";

    /** A delivery or read receipt advanced an existing row. */
    public static final String KIND_STATUS = "STATUS";

    /** A task became actionable. Carried here so the shell's task poller can retire. */
    public static final String KIND_TASK = "TASK";

    /**
     * Not an event at all: the first frame of a stream, handing the browser its connection id.
     *
     * <p>Never published and never crosses Redis. It exists because a browser cannot otherwise name
     * its own connection, and naming it is what lets it declare which deal it is looking at. See
     * {@code WhatsappEventService.watch}.
     */
    public static final String KIND_INIT = "INIT";

    /** Routing, not payload: which tenant's browsers this belongs to. */
    private String appCode;

    private String clientCode;

    /** The deal. The only thing the client needs in order to go and ask for the rest. */
    private BigInteger ticketId;

    private String kind;

    /**
     * The three columns the access filter tests, copied off the deal at publish.
     *
     * <p>They are a snapshot. A deal reassigned between the publish and the fan-out is routed by who
     * owned it a millisecond ago, which is the same staleness window the client-fetch version had
     * and is not worth closing: the next event corrects it, and the thread itself is fetched through
     * the real read either way.
     */
    private BigInteger clientId;

    private BigInteger assignedUserId;

    private BigInteger createdBy;

    /** For the toast. Present only because the fan-out has already decided this browser may see it. */
    private String dealName;

    /** For the toast's deep link into {@code /dealProfile/<code>?tab=Whatsapp}. */
    private String dealCode;

    /** Set on the {@link #KIND_INIT} frame only. */
    private String connectionId;

    /**
     * Epoch millis, stamped at publish.
     *
     * <p>Two jobs. It dates the event for a log, and it makes two otherwise identical events
     * distinguishable, which is what lets the UI treat a repeat ping on the same deal as a new
     * event rather than a no-op.
     *
     * <p>Epoch millis rather than a {@code LocalDateTime} on purpose: this crosses machines, and an
     * unzoned local timestamp written by one server and read by another is the bug class that put
     * deal activity three hours into the future.
     */
    private long at;
}
