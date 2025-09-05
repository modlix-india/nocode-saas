package com.fincity.saas.entity.processor.model.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CampaignTicketRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -7498763776700741521L;

    private String appCode;
    private String clientCode;
    private LeadDetails leadDetails;
    private CampaignDetails campaignDetails;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class LeadDetails implements Serializable {

        @Serial
        private static final long serialVersionUID = -6744028533634824904L;

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
        private String subSource;
        private String source;

        // Any extra payload
        private Map<String, Object> customFields;

        // Convenience helpers (optional)
        public boolean hasIdentifyInfo() {
            return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
        }

        public boolean hasSourceInfo() {
            return source != null && !source.isBlank();
        }
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class CampaignDetails implements Serializable {
        @Serial
        private static final long serialVersionUID = 2025435342246065791L;

        private String adId;
        private String adName;
        private String campaignId;
        private String campaignName;
        private String adSetId;
        private String adSetName;
    }
}