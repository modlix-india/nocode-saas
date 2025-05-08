package com.fincity.saas.entity.collector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class LeadDetails {

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

}
