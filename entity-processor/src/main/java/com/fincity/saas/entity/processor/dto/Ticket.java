package com.fincity.saas.entity.processor.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.eager.relations.resolvers.field.ClientFieldResolver;
import com.fincity.saas.entity.processor.eager.relations.resolvers.field.UserFieldResolver;
import com.fincity.saas.entity.processor.enums.EntitySeries;

import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.entity.processor.model.common.RuleResult;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import com.fincity.saas.entity.processor.model.request.form.WalkInFormTicketRequest;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.util.NameUtil;
import com.fincity.saas.entity.processor.util.PhoneUtil;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
@IgnoreGeneration
public class Ticket extends BaseProcessorDto<Ticket> {

    @Serial
    private static final long serialVersionUID = 1639822311147907381L;

    // formData payload keys; mirrors MetaEntityUtil.FD_* on the collector side.
    private static final String FORM_DATA_PROVIDER = "provider";
    private static final String FORM_DATA_STANDARD = "standard";
    private static final String FORM_DATA_CUSTOM = "custom";

    private ULong ownerId;
    private ULong assignedUserId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;

    /**
     * The number this deal is messaged on, when it differs from the one it is called on.
     *
     * <p>Null is the ordinary case, not missing data. Most leads are reachable on one number and
     * copying it into a second column would only let the two drift. It fills in the other way round:
     * a lead gives a number at intake, messaging it goes nowhere, someone rings them, and they name a
     * different number to use on WhatsApp.
     *
     * <p>Read through {@link #whatsappOrPhoneNumber()} rather than directly, everywhere. A caller
     * that reads this field raw sends nothing at all for the overwhelming majority of deals.
     */
    private String whatsappNumber;

    /** Calling code for {@link #whatsappNumber}. Null exactly when that is. */
    private Integer whatsappDialCode;

    private String email;
    private ULong productId;
    private ULong stage;
    private ULong status;
    private String source;
    private String subSource;
    private ULong campaignId;
    private ULong adsetId;
    private ULong adId;
    private Boolean dnc = Boolean.FALSE;
    private String tag;
    private Map<String, Object> metaData;
    private Map<String, Object> adData;
    private Map<String, Object> formData;

    private ULong productTemplateId = null;
    private LocalDateTime latestTaskDueDate;
    private LocalDateTime expiresOn;
    private String latestComment;

    /**
     * Most recent WhatsApp message on this deal, either direction. Orders the conversation list.
     * Written only by {@code TicketDAO.touchLastMessageAt}, never through the normal update path,
     * so that a message does not register as an edit in the deal's audit trail.
     */
    private LocalDateTime lastMessageAt;

    /**
     * The lead asked not to be contacted on WhatsApp.
     *
     * <p>Permanent, and deliberately on the deal rather than in a rule: a stage change re-runs the
     * rules, and a rule set that re-enrols an opted-out lead is precisely the complaint that becomes
     * a report against the number. Checked before every automated send.
     */
    private Boolean whatsappOptedOut = Boolean.FALSE;

    private LocalDateTime whatsappOptedOutAt;

    /**
     * The inbound message that triggered it.
     *
     * <p>Kept because detection is a text match and text matches produce false positives. Without
     * the original message nobody can tell an opt-out from a lead who happened to write "stop by
     * tomorrow", and the flag is permanent, so a wrong one is unreversible in practice.
     */
    private String whatsappOptedOutText;

    /**
     * The customer's WhatsApp avatar.
     *
     * <p>On the deal rather than on a message, because it belongs to whoever is on the other end of
     * the number and not to anything they said. Written to every deal sharing that number, so a
     * customer holding several does not appear as several different people.
     *
     * <p>Outside attachment retention, deliberately. Media in a thread expires after thirty days and
     * says so; an avatar vanishing on the same schedule would just look broken.
     */
    private FileDetail whatsappProfilePicFileDetail;

    /** WhatsApp's id for that image, so an unchanged picture is never fetched twice. */
    private String whatsappProfilePicId;

    @JsonIgnore
    private transient RuleResult assignmentRuleResult;

    @JsonIgnore
    private transient Map<String, Object> evaluationTrace;

    public Ticket() {
        super();
        this.relationsMap.put(Fields.ownerId, EntitySeries.OWNER.getTable());
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
        this.relationsMap.put(Fields.stage, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.status, EntitySeries.STAGE.getTable());
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.assignedUserId);
        this.relationsResolverMap.put(ClientFieldResolver.class, BaseProcessorDto.Fields.clientId);
        this.relationsMap.put(Fields.campaignId, EntitySeries.CAMPAIGN.getTable());
        this.relationsMap.put(Fields.adsetId, EntitySeries.ADSET.getTable());
        this.relationsMap.put(Fields.adId, EntitySeries.AD.getTable());
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

    public Ticket(Ticket ticket) {
        super(ticket);
        this.ownerId = ticket.ownerId;
        this.assignedUserId = ticket.assignedUserId;
        this.dialCode = ticket.dialCode;
        this.phoneNumber = ticket.phoneNumber;
        this.whatsappNumber = ticket.whatsappNumber;
        this.whatsappDialCode = ticket.whatsappDialCode;
        this.email = ticket.email;
        this.productId = ticket.productId;
        this.stage = ticket.stage;
        this.status = ticket.status;
        this.source = ticket.source;
        this.subSource = ticket.subSource;
        this.campaignId = ticket.campaignId;
        this.adsetId = ticket.adsetId;
        this.adId = ticket.adId;
        this.dnc = ticket.dnc;
        this.tag = ticket.tag;
        this.metaData = CloneUtil.cloneMapObject(ticket.metaData);
        this.adData = CloneUtil.cloneMapObject(ticket.adData);
        this.formData = CloneUtil.cloneMapObject(ticket.formData);
        this.productTemplateId = ticket.productTemplateId;
        this.latestTaskDueDate = ticket.latestTaskDueDate;
        this.expiresOn = ticket.expiresOn;
        this.latestComment = ticket.latestComment;
        this.lastMessageAt = ticket.lastMessageAt;
        this.whatsappOptedOut = ticket.whatsappOptedOut;
        this.whatsappOptedOutAt = ticket.whatsappOptedOutAt;
        this.whatsappOptedOutText = ticket.whatsappOptedOutText;
        this.whatsappProfilePicFileDetail = ticket.whatsappProfilePicFileDetail;
        this.whatsappProfilePicId = ticket.whatsappProfilePicId;
        this.assignmentRuleResult = ticket.assignmentRuleResult;
        this.evaluationTrace = ticket.evaluationTrace;
    }

    /**
     * The number to message this deal on.
     *
     * <p>Every WhatsApp path goes through this and none of them reads {@link #whatsappNumber}
     * directly. The separate number is the exception rather than the rule, so a caller that reads the
     * field raw works on the handful of deals someone has corrected and silently sends nothing on all
     * the rest - a failure that looks like the message never being written.
     *
     * <p>Blank counts as absent, not as a number. An empty string reaches here from a form that
     * submitted a cleared field, and treating it as set would send to nowhere while looking correct
     * in the database.
     *
     * <p>Deliberately not named {@code getX}: Jackson would serialise it, the client would post it
     * back, and a derived value would start being stored as if someone had typed it.
     */
    public String whatsappOrPhoneNumber() {
        return StringUtil.safeIsBlank(this.whatsappNumber) ? this.phoneNumber : this.whatsappNumber;
    }

    /** The calling code that goes with {@link #whatsappOrPhoneNumber()}, from the same source. */
    public Integer whatsappOrPhoneDialCode() {
        return StringUtil.safeIsBlank(this.whatsappNumber) ? this.dialCode : this.whatsappDialCode;
    }

    /** Whether someone has recorded a WhatsApp number that differs from the number on file. */
    public boolean hasSeparateWhatsappNumber() {
        return !StringUtil.safeIsBlank(this.whatsappNumber);
    }

    public static Ticket of(TicketRequest ticketRequest) {
        return new Ticket()
                .setDialCode(
                        ticketRequest.getPhoneNumber() != null
                                ? ticketRequest.getPhoneNumber().getCountryCode()
                                : null)
                .setPhoneNumber(
                        ticketRequest.getPhoneNumber() != null
                                ? ticketRequest.getPhoneNumber().getNumber()
                                : null)
                .setWhatsappDialCode(
                        ticketRequest.getWhatsappNumber() != null
                                ? ticketRequest.getWhatsappNumber().getCountryCode()
                                : null)
                .setWhatsappNumber(
                        ticketRequest.getWhatsappNumber() != null
                                ? ticketRequest.getWhatsappNumber().getNumber()
                                : null)
                .setEmail(
                        ticketRequest.getEmail() != null
                                ? ticketRequest.getEmail().getAddress()
                                : null)
                .setSource(ticketRequest.getSource())
                .setSubSource(ticketRequest.getSubSource() != null ? ticketRequest.getSubSource() : null)
                .setName(ticketRequest.getName())
                .setDescription(ticketRequest.getDescription());
    }

    public static Ticket of(CampaignTicketRequest campaignTicketRequest) {
        Ticket ticket = new Ticket()
                .setDialCode(
                        campaignTicketRequest.getLeadDetails().getPhone() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getPhone()
                                        .getCountryCode()
                                : null)
                .setPhoneNumber(
                        campaignTicketRequest.getLeadDetails().getPhone() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getPhone()
                                        .getNumber()
                                : null)
                // Meta lead forms have had a WhatsApp-number field all along and the collector has
                // always parsed it. Until this column existed the value could only be folded into the
                // formData blob below, which nothing queries and no screen reads, so a lead who
                // volunteered their WhatsApp number at intake was messaged on the other one anyway.
                .setWhatsappDialCode(
                        campaignTicketRequest.getLeadDetails().getWhatsappNumber() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getWhatsappNumber()
                                        .getCountryCode()
                                : null)
                .setWhatsappNumber(
                        campaignTicketRequest.getLeadDetails().getWhatsappNumber() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getWhatsappNumber()
                                        .getNumber()
                                : null)
                .setEmail(
                        campaignTicketRequest.getLeadDetails().getEmail() != null
                                ? campaignTicketRequest
                                        .getLeadDetails()
                                        .getEmail()
                                        .getAddress()
                                : null)
                .setSource(campaignTicketRequest.getLeadDetails().getSource())
                .setSubSource(
                        campaignTicketRequest.getLeadDetails().getSubSource() != null
                                ? campaignTicketRequest.getLeadDetails().getSubSource()
                                : null)
                .setName(
                        campaignTicketRequest.getLeadDetails().getFullName() != null
                                ? campaignTicketRequest.getLeadDetails().getFullName()
                                : campaignTicketRequest.getLeadDetails().getFirstName() + " "
                                        + campaignTicketRequest.getLeadDetails().getLastName());

        if (campaignTicketRequest.getCampaignDetails() != null) {
            Map<String, Object> metaData = new HashMap<>();
            CampaignTicketRequest.CampaignDetails cd = campaignTicketRequest.getCampaignDetails();

            if (!StringUtil.safeIsBlank(cd.getKeyword())) metaData.put("keyword", cd.getKeyword());

            if (!metaData.isEmpty()) ticket.setMetaData(metaData);
        }

        if (campaignTicketRequest.getLeadDetails().getAdData() != null
                && !campaignTicketRequest.getLeadDetails().getAdData().isEmpty()) {
            ticket.setAdData(new HashMap<>(campaignTicketRequest.getLeadDetails().getAdData()));
        }

        ticket.setFormData(buildFormData(campaignTicketRequest.getLeadDetails()));

        return ticket;
    }

    /**
     * Folds the lead-form submission onto the ticket so nothing the form captured is lost.
     *
     * <p>The Meta collector already supplies a complete {@code formData}: provenance, the
     * normalized {@code standard} / {@code custom} answer maps, and a verbatim Graph API snapshot.
     * The website path supplies none, so {@code standard} is rebuilt here from the typed
     * {@code LeadDetails} fields and {@code custom} falls back to {@code customFields}.
     * Collector-supplied entries win on conflict because they preserve multi-answer questions as
     * lists, which the flat typed fields cannot represent.
     *
     * <p>Only phone, email and name have real ticket columns. Every other answer ({@code city},
     * {@code dob}, {@code gender}, {@code jobTitle}, {@code zipCode}, custom questions and the
     * rest) reaches the database only through here.
     */
    private static Map<String, Object> buildFormData(CampaignTicketRequest.LeadDetails lead) {

        if (lead == null) return null;

        Map<String, Object> formData =
                lead.getFormData() != null ? new LinkedHashMap<>(lead.getFormData()) : new LinkedHashMap<>();

        Map<String, Object> standard = new LinkedHashMap<>();
        putIfPresent(standard, "email", lead.getEmail() != null ? lead.getEmail().getAddress() : null);
        putIfPresent(standard, "fullName", lead.getFullName());
        putIfPresent(standard, "phone", phoneText(lead.getPhone()));
        putIfPresent(standard, "companyName", lead.getCompanyName());
        putIfPresent(standard, "workEmail", lead.getWorkEmail() != null ? lead.getWorkEmail().getAddress() : null);
        putIfPresent(standard, "workPhoneNumber", phoneText(lead.getWorkPhoneNumber()));
        putIfPresent(standard, "jobTitle", lead.getJobTitle());
        putIfPresent(standard, "militaryStatus", lead.getMilitaryStatus());
        putIfPresent(standard, "relationshipStatus", lead.getRelationshipStatus());
        putIfPresent(standard, "maritalStatus", lead.getMaritalStatus());
        putIfPresent(standard, "gender", lead.getGender());
        putIfPresent(standard, "dob", lead.getDob());
        putIfPresent(standard, "firstName", lead.getFirstName());
        putIfPresent(standard, "lastName", lead.getLastName());
        putIfPresent(standard, "zipCode", lead.getZipCode());
        putIfPresent(standard, "postCode", lead.getPostCode());
        putIfPresent(standard, "country", lead.getCountry());
        putIfPresent(standard, "province", lead.getProvince());
        putIfPresent(standard, "streetAddress", lead.getStreetAddress());
        putIfPresent(standard, "state", lead.getState());
        putIfPresent(standard, "city", lead.getCity());
        putIfPresent(standard, "whatsappNumber", phoneText(lead.getWhatsappNumber()));

        if (formData.get(FORM_DATA_STANDARD) instanceof Map<?, ?> collectorStandard)
            collectorStandard.forEach((key, value) -> standard.put(String.valueOf(key), value));

        if (!standard.isEmpty()) formData.put(FORM_DATA_STANDARD, standard);

        if (!formData.containsKey(FORM_DATA_CUSTOM)
                && lead.getCustomFields() != null
                && !lead.getCustomFields().isEmpty())
            formData.put(FORM_DATA_CUSTOM, new LinkedHashMap<>(lead.getCustomFields()));

        if (!StringUtil.safeIsBlank(lead.getPlatform()))
            formData.putIfAbsent(FORM_DATA_PROVIDER, lead.getPlatform());

        return formData.isEmpty() ? null : formData;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (!StringUtil.safeIsBlank(value)) target.put(key, value);
    }

    private static String phoneText(PhoneNumber phone) {
        if (phone == null || StringUtil.safeIsBlank(phone.getNumber())) return null;

        // PhoneUtil.parse stores the number in E164 form, so the dial code is already on it.
        // Only prefix when a caller built the object with raw setters and skipped parsing.
        String number = phone.getNumber();
        if (number.startsWith("+") || phone.getCountryCode() == null) return number;

        return "+" + phone.getCountryCode() + number;
    }

    public static Ticket of(WalkInFormTicketRequest walkInFormTicketRequest) {
        return new Ticket()
                .setDialCode(
                        walkInFormTicketRequest.getPhoneNumber() != null
                                ? walkInFormTicketRequest.getPhoneNumber().getCountryCode()
                                : null)
                .setPhoneNumber(
                        walkInFormTicketRequest.getPhoneNumber() != null
                                ? walkInFormTicketRequest.getPhoneNumber().getNumber()
                                : null)
                .setEmail(
                        walkInFormTicketRequest.getEmail() != null
                                ? walkInFormTicketRequest.getEmail().getAddress()
                                : null)
                .setSubSource(
                        walkInFormTicketRequest.getSubSource() != null ? walkInFormTicketRequest.getSubSource() : null)
                .setName(walkInFormTicketRequest.getName())
                .setDescription(walkInFormTicketRequest.getDescription());
    }

    @Override
    @JsonIgnore
    public ULong getAccessUser() {
        return this.getAssignedUserId();
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET;
    }

    @JsonIgnore
    public boolean isExpired() {
        return this.expiresOn != null && this.expiresOn.isBefore(LocalDateTime.now());
    }

    public Ticket setSource(String source) {
        if (StringUtil.safeIsBlank(source)) return this;

        this.source = NameUtil.normalize(source);
        return this;
    }

    public Ticket setSubSource(String subSource) {
        if (StringUtil.safeIsBlank(subSource)) return this;

        this.subSource = NameUtil.normalize(subSource);
        return this;
    }
}
