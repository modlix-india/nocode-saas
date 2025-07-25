package com.fincity.saas.message.configuration.call.exotel;

public class ExotelApiConfig {

    public static final String SUB_DOMAIN = "api.exotel.com";

    private ExotelApiConfig() {}

    public static String getCallUrl(String accountSid) {
        return "/v1/Accounts/" + accountSid + "/Calls/connect";
    }
}
