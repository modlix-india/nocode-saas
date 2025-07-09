package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.entity.collector.enums.LeadSource;
import com.fincity.saas.entity.collector.enums.LeadSubSource;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public abstract class AbstractLeadBase implements Serializable {

    @Serial
    private static final long serialVersionUID = 3814596747030554670L;

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
    private LeadSubSource subSource;
    private LeadSource source;
    private Map<String, Object> customFields;
}
