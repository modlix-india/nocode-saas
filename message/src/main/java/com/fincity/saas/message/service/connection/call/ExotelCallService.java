package com.fincity.saas.message.service.connection.call;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Base64;

@Service
public class ExotelCallService extends AbstractCallService implements IAppCallService {

    private static final String EXOTEL_API_URL_TEMPLATE = "https://%s:%s@%s/v1/Accounts/%s/Calls/connect";

    @Override
    public Mono<Boolean> makeCall(String fromNumber, String toNumber, String callerId, Connection connection) {

        if (connection.getConnectionDetails() == null) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                    "Exotel connection details");
        }

        // Extract connection details
        String apiKey = getConnectionDetail(connection, "apiKey");
        String apiToken = getConnectionDetail(connection, "apiToken");
        String accountSid = getConnectionDetail(connection, "accountSid");
        String subdomain = getConnectionDetail(connection, "subdomain", "api.exotel.com");

        // Validate required parameters
        if (StringUtil.safeIsBlank(apiKey) || StringUtil.safeIsBlank(apiToken) || StringUtil.safeIsBlank(accountSid)) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                    "Exotel API credentials");
        }

        // Validate phone numbers
        fromNumber = validatePhoneNumber(fromNumber);
        toNumber = validatePhoneNumber(toNumber);

        if (StringUtil.safeIsBlank(fromNumber) || StringUtil.safeIsBlank(toNumber)) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                    "Phone numbers cannot be empty");
        }

        if (StringUtil.safeIsBlank(callerId)) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                    "Caller ID cannot be empty");
        }

        // Construct the API URL
        String apiUrl = String.format(EXOTEL_API_URL_TEMPLATE, apiKey, apiToken, subdomain, accountSid);

        // Prepare request parameters
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("From", fromNumber);
        formData.add("To", toNumber);
        formData.add("CallerId", callerId);
        formData.add("Record", "false");

        // Make the API call
        return FlatMapUtil.flatMapMono(
                () -> WebClient.create()
                        .post()
                        .uri(apiUrl)
                        .body(BodyInserters.fromFormData(formData))
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorResume(WebClientResponseException.class, e -> {
                            logger.error("Error calling Exotel API: {}", e.getResponseBodyAsString(), e);
                            return this.msgService.throwMessage(
                                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                    CoreMessageResourceService.UNABLE_TO_FETCH_EXTERNAL_RESOURCE,
                                    "Exotel API error: " + e.getResponseBodyAsString(),
                                    e);
                        }),
                response -> {
                    logger.debug("Exotel API response: {}", response);
                    return Mono.just(true);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCall"));
    }

    private String getConnectionDetail(Connection connection, String key) {
        return getConnectionDetail(connection, key, null);
    }

    private String getConnectionDetail(Connection connection, String key, String defaultValue) {
        if (connection.getConnectionDetails() == null || !connection.getConnectionDetails().containsKey(key)) {
            return defaultValue;
        }
        Object value = connection.getConnectionDetails().get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
