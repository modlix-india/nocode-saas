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
 * <h2>Addressed, not broadcast</h2>
 *
 * <p>{@link #recipients} is the whole routing story: it is resolved once at publish, by
 * {@code TicketAudienceService} inverting the condition {@code TicketDAO} puts on every deal read,
 * and the fan-out on each instance is a membership test against it.
 *
 * <p>Two designs preceded it and both are worth remembering, because each looked right.
 *
 * <p><b>Broadcast to the tenant, id only.</b> Every logged-in browser received every event and
 * fetched the ticket through the normal read, which is what enforced access. Correct by
 * construction, and it did not scale: twenty people with a handful of tabs turned one message into
 * hundreds of authenticated ticket reads, each running a sub-organisation expansion, nearly all
 * discarded as 403s.
 *
 * <p><b>Re-evaluate the rule per connection.</b> The event carried the deal's {@code clientId},
 * {@code assignedUserId} and {@code createdBy} and each instance applied the access rule itself.
 * This was wrong in both directions: it copied one branch of a rule that has three, ignored the
 * per-product rule engine entirely, compared the wrong client code for business partners, and
 * required every connection to hold its subscriber's entire sub-organisation in memory for the life
 * of the connection.
 *
 * <p>Resolving the audience once, where the rule lives, is cheaper than both and has one place to be
 * wrong instead of two.
 *
 * <h2>Why it may carry a name and a body</h2>
 *
 * <p>Because only an entitled user receives it. That is a real trade: this crosses a Redis channel
 * shared by every instance and every tenant, and the guard is now an inversion of the read rule
 * rather than the read itself. It is defensible only while {@code TicketAudienceServiceTest} keeps
 * asserting that a user is in the audience exactly when the real read returns the row for them. If
 * that rule changes and the test is deleted rather than fixed, this class should go back to carrying
 * an id alone and the client should go back to fetching.
 *
 * <p>It still carries no phone number, and no media: the thread is fetched for anything richer than
 * a line of text.
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
     * Exactly who may receive this, resolved at publish by inverting the deal read rule.
     *
     * <p>The fan-out is a membership test against this list and nothing else. An earlier version
     * carried the deal's {@code clientId}, {@code assignedUserId} and {@code createdBy} instead and
     * had each instance re-evaluate the access rule per connection. That reimplemented a rule with
     * three user branches plus a whole product-rule engine, got it wrong in both directions, and
     * required every connection to hold its subscriber's entire sub-organisation in memory. See
     * {@code TicketAudienceService}.
     *
     * <p>A snapshot, like anything computed at publish. A deal reassigned a millisecond later is
     * routed by who owned it a millisecond ago; the next event corrects it and the thread itself is
     * always fetched through the real read.
     */
    private java.util.List<BigInteger> recipients;

    /**
     * The deal's product.
     *
     * <p>Needed because the inbox is product-filtered: a browser scoped to one product has to know
     * whether a ping about another concerns its list at all.
     */
    private BigInteger productId;

    /** For the toast. Present only because the fan-out has already decided this browser may see it. */
    private String dealName;

    /** For the toast's deep link into {@code /dealProfile/<code>?tab=Whatsapp}. */
    private String dealCode;

    /**
     * The message text, for a thread that is already open and for the toast's preview.
     *
     * <p>Present only because {@link #recipients} is authoritative. It raises the cost of an
     * audience bug from disclosing a name to disclosing what a customer wrote, which is why the
     * equivalence test in {@code TicketAudienceServiceTest} is not optional.
     *
     * <p>Null for a status receipt, which has no body, and for media arriving without a caption.
     */
    private String body;

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
