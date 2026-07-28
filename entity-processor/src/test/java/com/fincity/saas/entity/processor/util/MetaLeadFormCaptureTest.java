package com.fincity.saas.entity.processor.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;
import com.fincity.saas.entity.processor.model.LeadDetails;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.request.CampaignTicketRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the lead-form capture path: every answer a Meta form collects has to survive into
 * {@code LeadDetails.formData} and then onto {@code Ticket.formData}.
 *
 * <p>Two losses used to happen in {@code buildLeadDetails} before the EntityResponse was even
 * built, so no ticket-side change could recover them: only {@code values[0]} was read, and any
 * question type outside {@link com.fincity.saas.entity.processor.enums.MetaLeadFieldType} was
 * discarded with no log line.
 */
class MetaLeadFormCaptureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final MetaEntityUtil.ExtractPayload EXTRACT =
            new MetaEntityUtil.ExtractPayload("FORM_1", "LEADGEN_1", "AD_1");

    private static JsonNode json(String raw) throws JsonProcessingException {
        return MAPPER.readTree(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> formData, String key) {
        Object value = formData.get(key);
        assertInstanceOf(Map.class, value, key + " should be a map");
        return (Map<String, Object>) value;
    }

    /**
     * A form with a standard question, a multi-answer CUSTOM question, a question type we do not
     * map, and an answer whose {@code name} never appears in {@code questions}. Everything must
     * land somewhere.
     */
    @Test
    void everyAnswerSurvivesIncludingUnmappedTypesAndOrphanKeys() throws Exception {

        JsonNode form = json("""
                {"questions":[
                  {"key":"email","type":"EMAIL","label":"Email"},
                  {"key":"full_name","type":"FULL_NAME","label":"Full name"},
                  {"key":"city","type":"CITY","label":"City"},
                  {"key":"amenities_x1","type":"CUSTOM","label":"Which amenities matter?"},
                  {"key":"movein_x2","type":"MULTIPLE_CHOICE","label":"Preferred move-in"}
                ]}""");

        JsonNode lead = json("""
                {"id":"LEADGEN_1","field_data":[
                  {"name":"email","values":["asha@example.com"]},
                  {"name":"full_name","values":["Asha Rao"]},
                  {"name":"city","values":["Bengaluru"]},
                  {"name":"amenities_x1","values":["Pool","Gym","Parking"]},
                  {"name":"movein_x2","values":["2026-09"]},
                  {"name":"orphan_key","values":["stray answer"]}
                ]}""");

        LeadDetails result = MetaEntityUtil.buildLeadDetails(lead, form, EXTRACT).block();

        assertNotNull(result);

        // Standard questions still populate the typed fields, unchanged behaviour.
        assertEquals("asha@example.com", result.getEmail());
        assertEquals("Asha Rao", result.getFullName());
        assertEquals("Bengaluru", result.getCity());

        Map<String, Object> formData = result.getFormData();
        assertNotNull(formData, "formData must be populated");
        assertEquals("FACEBOOK_FORM", formData.get("provider"));
        assertEquals("FORM_1", formData.get("formId"));
        assertEquals("LEADGEN_1", formData.get("leadGenId"));

        Map<String, Object> standard = section(formData, "standard");
        assertEquals("asha@example.com", standard.get("email"));
        assertEquals("Asha Rao", standard.get("fullName"));
        assertEquals("Bengaluru", standard.get("city"));

        Map<String, Object> custom = section(formData, "custom");

        // Multi-select answers keep every value, not just values[0].
        assertEquals(List.of("Pool", "Gym", "Parking"), custom.get("Which amenities matter?"));

        // MULTIPLE_CHOICE is outside MetaLeadFieldType; this used to be discarded silently.
        assertEquals("2026-09", custom.get("Preferred move-in"));

        // field_data name with no matching questions[].key, so type is null: keyed by raw key.
        assertEquals("stray answer", custom.get("orphan_key"));

        // customFields stays populated and now mirrors the custom map.
        assertEquals(custom, result.getCustomFields());

        Map<String, Object> raw = section(formData, "raw");
        assertEquals(6, ((List<?>) raw.get("fieldData")).size(), "raw snapshot keeps all answers");
        assertEquals(5, ((List<?>) raw.get("questions")).size(), "raw snapshot keeps the form definition");
    }

    /**
     * A multi-answer question mapped to a typed field cannot store a list there, since every
     * LeadDetails field is a String. It gets a joined value while formData keeps the list.
     */
    @Test
    void standardMultiValueIsJoinedOnTypedFieldAndKeptAsListInFormData() throws Exception {

        JsonNode form = json("""
                {"questions":[{"key":"city","type":"CITY","label":"City"}]}""");

        JsonNode lead = json("""
                {"id":"LEADGEN_1","field_data":[
                  {"name":"city","values":["Bengaluru","Mysuru"]}
                ]}""");

        LeadDetails result = MetaEntityUtil.buildLeadDetails(lead, form, EXTRACT).block();

        assertNotNull(result);
        assertEquals("Bengaluru, Mysuru", result.getCity());
        assertEquals(List.of("Bengaluru", "Mysuru"), section(result.getFormData(), "standard").get("city"));
    }

    /** An answer with an empty values array must not become an empty list. */
    @Test
    void emptyAnswerStaysAnEmptyString() throws Exception {

        JsonNode form = json("""
                {"questions":[{"key":"note_x1","type":"CUSTOM","label":"Anything else?"}]}""");

        JsonNode lead = json("""
                {"id":"LEADGEN_1","field_data":[{"name":"note_x1","values":[]}]}""");

        LeadDetails result = MetaEntityUtil.buildLeadDetails(lead, form, EXTRACT).block();

        assertNotNull(result);
        assertEquals("", section(result.getFormData(), "custom").get("Anything else?"));
    }

    /** The collector's formData has to reach the ticket intact. */
    @Test
    void ticketCarriesCollectorFormDataThrough() {

        CampaignTicketRequest.LeadDetails lead = new CampaignTicketRequest.LeadDetails()
                .setEmail(Email.of("asha@example.com"))
                .setPhone(PhoneNumber.of(91, "9876543210"))
                .setFullName("Asha Rao")
                .setCity("Bengaluru")
                .setSource("SOCIAL_MEDIA")
                .setFormData(Map.of(
                        "provider", "FACEBOOK_FORM",
                        "formId", "FORM_1",
                        "custom", Map.of("Which amenities matter?", List.of("Pool", "Gym"))));

        Ticket ticket = Ticket.of(new CampaignTicketRequest().setLeadDetails(lead));

        Map<String, Object> formData = ticket.getFormData();
        assertNotNull(formData);
        assertEquals("FACEBOOK_FORM", formData.get("provider"));
        assertEquals("FORM_1", formData.get("formId"));
        assertEquals(List.of("Pool", "Gym"), section(formData, "custom").get("Which amenities matter?"));

        // Standard answers with no ticket column of their own reach the DB only through formData.
        assertEquals("Bengaluru", section(formData, "standard").get("city"));
    }

    /**
     * The website path supplies no formData, only customFields, so the ticket has to synthesize
     * both sections from the typed fields.
     */
    @Test
    void websiteLeadWithoutFormDataStillGetsStandardAndCustom() {

        CampaignTicketRequest.LeadDetails lead = new CampaignTicketRequest.LeadDetails()
                .setEmail(Email.of("ravi@example.com"))
                .setFullName("Ravi Kumar")
                .setCity("Pune")
                .setJobTitle("Architect")
                .setPlatform("Website")
                .setSource("Website")
                .setCustomFields(Map.of("Budget Range", "50L-75L"));

        Ticket ticket = Ticket.of(new CampaignTicketRequest().setLeadDetails(lead));

        Map<String, Object> formData = ticket.getFormData();
        assertNotNull(formData, "website leads must still capture their form answers");
        assertEquals("Website", formData.get("provider"));

        Map<String, Object> standard = section(formData, "standard");
        assertEquals("Pune", standard.get("city"));
        assertEquals("Architect", standard.get("jobTitle"));
        assertEquals("ravi@example.com", standard.get("email"));

        assertEquals("50L-75L", section(formData, "custom").get("Budget Range"));

        // No Meta call on this path, so there is no provider snapshot to keep.
        assertTrue(!formData.containsKey("raw"), "website path has no raw Meta snapshot");
    }

    /**
     * Both sides can produce a {@code standard} map. The collector's must win, because it can hold
     * a multi-answer question as a list while the flat typed field can only hold the joined string.
     */
    @Test
    void collectorStandardWinsOverTypedFieldsButGapsAreFilled() {

        CampaignTicketRequest.LeadDetails lead = new CampaignTicketRequest.LeadDetails()
                .setCity("Bengaluru, Mysuru")
                .setJobTitle("Architect")
                .setSource("SOCIAL_MEDIA")
                .setFormData(Map.of("standard", Map.of("city", List.of("Bengaluru", "Mysuru"))));

        Map<String, Object> standard =
                section(Ticket.of(new CampaignTicketRequest().setLeadDetails(lead)).getFormData(), "standard");

        // Collector value wins: the list survives instead of the flattened string.
        assertEquals(List.of("Bengaluru", "Mysuru"), standard.get("city"));

        // A field the collector did not send is still filled from the typed field.
        assertEquals("Architect", standard.get("jobTitle"));
    }

    /** A lead with nothing to record must leave the column NULL rather than writing {@code {}}. */
    @Test
    void emptyLeadLeavesFormDataNull() {

        CampaignTicketRequest.LeadDetails lead = new CampaignTicketRequest.LeadDetails().setSource("SOCIAL_MEDIA");

        assertNull(
                Ticket.of(new CampaignTicketRequest().setLeadDetails(lead)).getFormData(),
                "nothing captured means FORM_DATA stays NULL, not an empty object");
    }

    /** Phone answers keep their dial code, which a bare number field would drop. */
    @Test
    void phoneAnswersKeepTheirDialCode() {

        CampaignTicketRequest.LeadDetails lead = new CampaignTicketRequest.LeadDetails()
                .setPhone(PhoneNumber.of(91, "9876543210"))
                .setWhatsappNumber(PhoneNumber.of(91, "9876500000"))
                .setSource("SOCIAL_MEDIA");

        Map<String, Object> standard =
                section(Ticket.of(new CampaignTicketRequest().setLeadDetails(lead)).getFormData(), "standard");

        assertEquals("+919876543210", standard.get("phone"));
        assertEquals("+919876500000", standard.get("whatsappNumber"));
    }

    /** The copy constructor has to deep-clone formData, not share the reference. */
    @Test
    void copyConstructorDeepClonesFormData() {

        CampaignTicketRequest.LeadDetails lead = new CampaignTicketRequest.LeadDetails()
                .setCity("Bengaluru")
                .setSource("SOCIAL_MEDIA")
                .setCustomFields(Map.of("Budget Range", "50L-75L"));

        Ticket original = Ticket.of(new CampaignTicketRequest().setLeadDetails(lead));
        Ticket copy = new Ticket(original);

        assertNotNull(copy.getFormData());
        assertEquals(original.getFormData(), copy.getFormData());
        assertNotSame(original.getFormData(), copy.getFormData(), "formData must be cloned, not shared");
    }

    /** buildLeadDetails is also reachable with a null ExtractPayload; it must not blow up. */
    @Test
    void nullExtractPayloadIsTolerated() throws Exception {

        JsonNode form = json("""
                {"questions":[{"key":"email","type":"EMAIL","label":"Email"}]}""");
        JsonNode lead = json("""
                {"field_data":[{"name":"email","values":["asha@example.com"]}]}""");

        LeadDetails result = MetaEntityUtil.buildLeadDetails(lead, form, null).block();

        assertNotNull(result);
        assertEquals("asha@example.com", result.getEmail());

        Map<String, Object> formData = result.getFormData();
        assertEquals("FACEBOOK_FORM", formData.get("provider"));
        assertTrue(!formData.containsKey("formId"), "no formId when there is no ExtractPayload");
    }

    /**
     * Guards the read contract of {@code GET /tickets/code/{code}/eager}. That endpoint selects
     * every table field and maps each column through {@code EagerUtil.fromJooqField}, so the new
     * column has to surface as {@code formData} in the JSON response.
     */
    @Test
    void eagerReadExposesTheColumnAsFormData() {
        assertEquals(
                "formData",
                EagerUtil.fromJooqField(
                        EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.FORM_DATA.getName()));
    }
}
