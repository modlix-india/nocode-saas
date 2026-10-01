package com.fincity.saas.message.configuration.call.exotel;

import java.net.URI;
import java.util.Collection;

public class ExotelApiConfig {

    /**
     * Host for the v1 telephony API when the connection does not name one. Regional accounts must set their own:
     * an India account answers {@code 401} here, indistinguishable from bad credentials, and works only on
     * {@code api.in.exotel.com}.
     */
    public static final String DEFAULT_API_SUBDOMAIN = "api.exotel.com";

    /**
     * Default recording domains: Exotel's own. Hosts vary by engine and region ({@code recordings.exotel.com},
     * {@code recordings.mum1.exotel.com}), all subdomains of it.
     */
    public static final String DEFAULT_RECORDING_DOMAINS = "exotel.com";

    private ExotelApiConfig() {}

    /**
     * Whether a recording URL may be fetched with the account's credentials: https, default port, no user info, on
     * a named domain or its subdomain. Load-bearing: the URL arrives on a permitAll callback, so fetching whatever
     * it says would send the API key and token anywhere. Only domains Exotel controls belong in the list.
     */
    public static boolean isRecordingUrl(String url, Collection<String> domains) {

        if (url == null || url.isBlank() || domains == null) return false;

        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getRawUserInfo() == null
                    && host != null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && domains.stream()
                            .map(String::trim)
                            .filter(domain -> !domain.isEmpty())
                            .anyMatch(domain -> host.equalsIgnoreCase(domain)
                                    || host.toLowerCase().endsWith("." + domain.toLowerCase()));
        } catch (Exception e) {
            return false;
        }
    }

    public static String getCallUrl() {
        return "/Calls/connect.json";
    }
}
