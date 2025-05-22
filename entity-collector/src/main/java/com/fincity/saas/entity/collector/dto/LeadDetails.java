package com.fincity.saas.entity.collector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.entity.collector.enums.LeadSource;
import com.fincity.saas.entity.collector.enums.LeadSubSource;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class LeadDetails implements Serializable {

    @Serial
    private static final long serialVersionUID = -369693871081491900L;

    private String email;
    private String fullName;
    private String phone;
    private String companyName;
    private String workEmail;
    private String workPhoneNumber;
    private String jobTitle;
    private String militaryStatus;
    private String relationshipStatus;
    private String maritalStatus;
    private String gender;
    private String dob;
    private String lastName;
    private String firstName;
    private String zipCode;
    private String postCode;
    private String country;
    private String province;
    private String streetAddress;
    private String state;
    private String city;
    private String whatsappNumber;
    private String clientCode;
    private String appCode;
    private String platform;
    private String source;
    private String subSource;
    private Map<String, String> customFields;


    public LeadDetails(WebsiteDetails details, EntityIntegration integration) {

        this.setEmail(details.getEmail());
        this.setFullName(details.getFullName());
        this.setPhone(details.getPhone());
        this.setCompanyName(details.getCompanyName());
        this.setWorkEmail(details.getWorkEmail());
        this.setWorkPhoneNumber(details.getWorkPhoneNumber());
        this.setJobTitle(details.getJobTitle());
        this.setMilitaryStatus(details.getMilitaryStatus());
        this.setRelationshipStatus(details.getRelationshipStatus());
        this.setMaritalStatus(details.getMaritalStatus());
        this.setGender(details.getGender());
        this.setDob(details.getDob());
        this.setLastName(details.getLastName());
        this.setFirstName(details.getFirstName());
        this.setZipCode(details.getZipCode());
        this.setPostCode(details.getPostCode());
        this.setCountry(details.getCountry());
        this.setProvince(details.getProvince());
        this.setStreetAddress(details.getStreetAddress());
        this.setState(details.getState());
        this.setCity(details.getCity());
        this.setWhatsappNumber(details.getWhatsappNumber());
        this.setSubSource(details.getSubSource());
        this.setCustomFields(details.getCustomFields());

        if ("FACEBOOK".equalsIgnoreCase(details.getUtm_source())) {
            populateStaticFields(this, integration, "FACEBOOK", LeadSource.WEBSITE, LeadSubSource.WEBSITE_FORM);
        } else {
            populateStaticFields(this, integration, "WEBSITE", LeadSource.WEBSITE,  Boolean.parseBoolean(String.valueOf(this.getSubSource()))

                    ? LeadSubSource.valueOf(this.getSubSource()) : LeadSubSource.WEBSITE_FORM);
        }
    }

    public LeadDetails() {

    }


    private static void populateStaticFields(
            LeadDetails lead,
            EntityIntegration integration,
            String platform,
            LeadSource source,
            LeadSubSource subSource
    ) {
        lead.setAppCode(integration.getAppCode());
        lead.setClientCode(integration.getClientCode());
        lead.setPlatform(platform);
        lead.setSource(String.valueOf(source));
        lead.setSubSource(String.valueOf(subSource));
    }

}
