package com.fincity.saas.message.configuration.call.exotel;

public class ExotelApiConfig {

    /**
     * Host for the v1 telephony API when the connection does not name one.
     *
     * <p>The global host, and the one this API was pinned to before the subdomain became a
     * connection detail — so an existing tenant that has never set {@code subdomain} keeps behaving
     * exactly as it did.
     *
     * <p>Regional accounts need their own and must say so on the connection: an India account
     * answers {@code 401 Unauthorized} on this host and works only on {@code api.in.exotel.com}.
     * That failure is indistinguishable from bad credentials, which is why the region belongs
     * somewhere an operator can see it rather than in a default here.
     */
    public static final String DEFAULT_API_SUBDOMAIN = "api.exotel.com";

    private ExotelApiConfig() {}

    public static String getCallUrl() {
        return "/Calls/connect.json";
    }
}
