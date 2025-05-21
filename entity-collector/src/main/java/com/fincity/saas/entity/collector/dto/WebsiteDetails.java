package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jooq.types.ULong;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class WebsiteDetails extends AbstractUpdatableDTO<ULong, ULong> implements Serializable {

    @Serial
    private static final long serialVersionUID = -126270115243553536L;

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
    private String subSource;
    private String utm_ad;
    private String utm_campaign;
    private String utm_adset;
    private String utm_source;
    private Map<String, String> customFields;
}