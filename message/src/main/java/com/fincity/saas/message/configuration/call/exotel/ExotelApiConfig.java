package com.fincity.saas.message.configuration.call.exotel;

public class ExotelApiConfig {

    public static final String BASE_DOMAIN = "https://api.exotel.com/v1/Accounts";

    private ExotelApiConfig() {}

    public static String getCallUrl() {
        return "/Calls/connect.json";
    }
}
