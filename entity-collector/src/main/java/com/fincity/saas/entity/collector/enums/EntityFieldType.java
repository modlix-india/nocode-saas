package com.fincity.saas.entity.collector.enums;


import lombok.Getter;

@Getter
public enum EntityFieldType {
    EMAIL("email"),
    FULL_NAME("fullName"),
    PHONE("phone"),
    COMPANY_NAME("companyName"),
    WORK_EMAIL("workEmail"),
    WORK_PHONE_NUMBER("workPhoneNumber"),
    JOB_TITLE("jobTitle"),
    MILITARY_STATUS("militaryStatus"),
    RELATIONSHIP_STATUS("relationshipStatus"),
    MARITAL_STATUS("maritalStatus"),
    GENDER("gender"),
    DOB("dob"),
    LAST_NAME("lastName"),
    FIRST_NAME("firstName"),
    ZIP_CODE("zipCode"),
    POST_CODE("postCode"),
    COUNTRY("country"),
    PROVINCE("province"),
    STREET_ADDRESS("streetAddress"),
    STATE("state"),
    CITY("city"),
    WHATSAPP_NUMBER("whatsappNumber");

    private final String fieldName;

    EntityFieldType(String fieldName) {
        this.fieldName = fieldName;
    }

    public static EntityFieldType fromType(String type) {
        if (type == null) return null;

        try {
            return EntityFieldType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
