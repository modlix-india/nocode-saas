package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.entity.collector.enums.LeadSource;
import com.fincity.saas.entity.collector.enums.LeadSubSource;
import java.io.Serializable;
import java.util.Map;

import com.fincity.saas.entity.collector.model.LeadDetails;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public abstract class AbstractLeadBase<T extends AbstractLeadBase<T>> implements Serializable {

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
    private String platform;
    private LeadSubSource subSource;
    private LeadSource source;
    private Map<String, Object> customFields;

    public T createLead(WebsiteDetails details) {

        this.email = details.getEmail();
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
        this.setCustomFields(details.getCustomFields());

        this.setSource(LeadSource.lookupLiteral(String.valueOf(details.getSource())));
        this.setSubSource(LeadSubSource.lookupLiteral(String.valueOf(details.getSubSource())));


        if ("FACEBOOK".equalsIgnoreCase(details.getUtmSource())) {
            this.setPlatform("FACEBOOK");
            this.setSource(LeadSource.WEBSITE);
            this.setSubSource(LeadSubSource.WEBSITE_FORM);
        } else {
            this.setPlatform("WEBSITE");
            this.setSource(LeadSource.WEBSITE);
            this.setSubSource(this.getSubSource() != null
                    ? this.getSubSource()
                    : LeadSubSource.WEBSITE_FORM);
        }

        return (T) this;
    }

}
