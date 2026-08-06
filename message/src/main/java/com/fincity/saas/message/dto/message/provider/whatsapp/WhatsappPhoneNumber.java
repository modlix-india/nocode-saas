package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.eager.relations.resolvers.field.ProductFieldResolver;
import com.fincity.saas.message.enums.bridge.WhatsappSessionState;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.CodeVerificationStatus;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.MessagingLimitTier;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.NameStatusType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.PlatformType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.QualityRatingType;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.Status;
import com.fincity.saas.message.model.message.whatsapp.business.WebhookConfig;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.QualityScore;
import com.fincity.saas.message.model.message.whatsapp.phone.Throughput;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

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
     * which corrupts the Signal ratchet beyond recovery. Null means unplaced, which for a Cloud API
     * era row is its permanent state.
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

    private ULong whatsappBusinessAccountId;
    private String displayPhoneNumber;
    private QualityRatingType qualityRating;
    private QualityScore qualityScore;
    private String verifiedName;
    private String phoneNumberId;
    private CodeVerificationStatus codeVerificationStatus;
    private NameStatusType nameStatus;
    private PlatformType platformType;
    private Throughput throughput;
    private Status status;
    private MessagingLimitTier messagingLimitTier;
    private Boolean isDefault = Boolean.FALSE;
    private WebhookConfig webhookConfig;

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
        this.whatsappBusinessAccountId = whatsappPhoneNumber.whatsappBusinessAccountId;
        this.displayPhoneNumber = whatsappPhoneNumber.displayPhoneNumber;
        this.qualityRating = whatsappPhoneNumber.qualityRating;
        this.qualityScore = whatsappPhoneNumber.qualityScore;
        this.verifiedName = whatsappPhoneNumber.verifiedName;
        this.phoneNumberId = whatsappPhoneNumber.phoneNumberId;
        this.codeVerificationStatus = whatsappPhoneNumber.codeVerificationStatus;
        this.nameStatus = whatsappPhoneNumber.nameStatus;
        this.platformType = whatsappPhoneNumber.platformType;
        this.throughput = whatsappPhoneNumber.throughput;
        this.status = whatsappPhoneNumber.status;
        this.messagingLimitTier = whatsappPhoneNumber.messagingLimitTier;
        this.isDefault = whatsappPhoneNumber.isDefault;
        this.webhookConfig = whatsappPhoneNumber.webhookConfig;
    }

    public static WhatsappPhoneNumber of(ULong whatsappBusinessAccountId, PhoneNumber phoneNumber) {
        return new WhatsappPhoneNumber()
                .setWhatsappBusinessAccountId(whatsappBusinessAccountId)
                .setDisplayPhoneNumber(phoneNumber.getDisplayPhoneNumber())
                .setQualityRating(phoneNumber.getQualityRating())
                .setVerifiedName(phoneNumber.getVerifiedName())
                .setPhoneNumberId(phoneNumber.getId())
                .setCodeVerificationStatus(phoneNumber.getCodeVerificationStatus())
                .setNameStatus(phoneNumber.getNameStatus())
                .setPlatformType(phoneNumber.getPlatformType())
                .setThroughput(phoneNumber.getThroughput())
                .setWebhookConfig(phoneNumber.getWebhookConfig());
    }

    public WhatsappPhoneNumber update(PhoneNumber phoneNumber) {
        this.qualityRating = phoneNumber.getQualityRating();
        this.verifiedName = phoneNumber.getVerifiedName();
        this.codeVerificationStatus = phoneNumber.getCodeVerificationStatus();
        this.nameStatus = phoneNumber.getNameStatus();
        this.platformType = phoneNumber.getPlatformType();
        this.throughput = phoneNumber.getThroughput();
        this.webhookConfig = phoneNumber.getWebhookConfig();
        return this;
    }

    public WhatsappPhoneNumber updateStatus(PhoneNumber phoneNumber) {
        this.qualityScore = phoneNumber.getQualityScore();
        this.status = phoneNumber.getStatus();
        this.nameStatus = phoneNumber.getNameStatus();
        this.messagingLimitTier = phoneNumber.getMessagingLimitTier();
        this.codeVerificationStatus = phoneNumber.getCodeVerificationStatus();
        return this;
    }
}
