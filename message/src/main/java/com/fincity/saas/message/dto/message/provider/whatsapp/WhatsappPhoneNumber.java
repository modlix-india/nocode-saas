package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.eager.relations.resolvers.field.ProductFieldResolver;
import com.fincity.saas.message.enums.bridge.WhatsappSessionState;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

/**
 * A linked WhatsApp number: which tenant and product it serves, which bridge instance holds it, and
 * what state that session is in.
 *
 * <p><b>This is the session table.</b> It was the Cloud API's phone-number registry and kept its
 * table and its name through the pivot, because {@code PHONE_NUMBER_ID}'s unique key is still the
 * inbound resolution path and renaming would have meant migrating live routing for no gain.
 *
 * <p>What it no longer carries is everything Meta used to tell us about a number: quality rating,
 * messaging limit tier, verification status, name-review status, platform type, throughput and the
 * webhook config. None of those concepts exist on the linked-device protocol. There is no review, no
 * tier and no webhook, so a field for each would be permanently null and would read as "we have not
 * synced yet" rather than "this cannot exist". The columns remain in the table, unmapped, until a
 * migration removes them.
 *
 * <p>The health of a number is now computed rather than reported: reply rate, warm-up day and the
 * caps all derive from the message history in entity-processor. That is strictly better, because
 * Meta's quality rating only moved after the damage was done.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappPhoneNumber extends BaseUpdatableDto<WhatsappPhoneNumber> {

    @Serial
    private static final long serialVersionUID = 9214491312043215338L;

    private ULong productId;

    /**
     * Eureka service id of whichever service owns conversations on this number. Inbound events
     * route to it, which is how this service stays a provider adapter and never learns what a
     * conversation means. Orthogonal to {@code productId}: a default number serving every product
     * still has exactly one owner. Null is unrouted, and such an event parks rather than dropping.
     */
    private String ownerService;

    /**
     * The bridge instance holding this session, and the whole of the routing table.
     *
     * <p>Authoritative data rather than a cache: a session is pinned to exactly one process, so
     * there is nothing to balance and nothing to hash. Consistent hashing would move sessions when
     * the fleet changes, and health-based failover would put two processes on one device store,
     * which corrupts the Signal ratchet beyond recovery. Null means unplaced.
     */
    private String bridgeInstanceId;

    private WhatsappSessionState sessionState;

    /** Why it is in that state. A BANNED row with no reason is unexplainable three months later. */
    private String sessionReason;

    /**
     * ISO country of the linked number, established at pair time from the linked JID.
     *
     * <p>Not from what the caller declared, which can be wrong, and not from the tenant, which is a
     * different thing entirely: a tenant operating in two countries has two numbers on two
     * instances, and the session is what carries the country.
     */
    private String country;

    /** When the number was linked. Drives the warm-up ramp, which is derived and must not be editable. */
    private LocalDateTime linkedAt;

    /**
     * When {@code sessionState} last changed.
     *
     * <p>Distinct from {@code updatedAt} so a dead session cannot reset its own retirement clock
     * merely by being reported on every heartbeat.
     */
    private LocalDateTime stateSince;

    /** The number itself, in display form. What a person recognises in the UI. */
    private String displayPhoneNumber;

    /**
     * The session id the bridge and entity-processor both address this number by.
     *
     * <p>Was Meta's phone number id. It keeps the column and its unique key because inbound
     * resolution goes through it, and because a stable opaque handle is what the rest of the
     * platform should be holding rather than a phone number.
     */
    private String phoneNumberId;

    /** Display name for the number, tenant-authored now rather than approved by anybody. */
    private String verifiedName;

    private Boolean isDefault = Boolean.FALSE;

    public WhatsappPhoneNumber() {
        super();
        this.relationsResolverMap.put(ProductFieldResolver.class, Fields.productId);
    }

    public WhatsappPhoneNumber(WhatsappPhoneNumber whatsappPhoneNumber) {
        super(whatsappPhoneNumber);
        this.productId = whatsappPhoneNumber.productId;
        this.ownerService = whatsappPhoneNumber.ownerService;
        this.bridgeInstanceId = whatsappPhoneNumber.bridgeInstanceId;
        this.sessionState = whatsappPhoneNumber.sessionState;
        this.sessionReason = whatsappPhoneNumber.sessionReason;
        this.country = whatsappPhoneNumber.country;
        this.linkedAt = whatsappPhoneNumber.linkedAt;
        this.stateSince = whatsappPhoneNumber.stateSince;
        this.displayPhoneNumber = whatsappPhoneNumber.displayPhoneNumber;
        this.phoneNumberId = whatsappPhoneNumber.phoneNumberId;
        this.verifiedName = whatsappPhoneNumber.verifiedName;
        this.isDefault = whatsappPhoneNumber.isDefault;
    }
}
