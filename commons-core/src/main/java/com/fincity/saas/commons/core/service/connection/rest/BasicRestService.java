package com.fincity.saas.commons.core.service.connection.rest;

import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.dto.RestRequest;
import com.fincity.saas.commons.core.dto.RestResponse;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.webclient.CustomUriBuilderFactory;
import com.fincity.saas.commons.webclient.WebClientEncodingModes;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class BasicRestService extends AbstractRestService {

    private static final String ENCODED_TEXT_TAG_DATA = "data:";
    private static final String ENCODED_TEXT_TAG_NAME = "name:";
    private static final String ENCODED_TEXT_TAG_BASE = "base64,";

    private static final String ENCODING_MODE = "encodingMode";

    private static byte[] decodeToFile(String base64EncodedFile) {
        try {
            return Base64.getDecoder().decode(base64EncodedFile);
        } catch (IllegalArgumentException e) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "Not a base64 encoded string", e);
        }
    }

    @Override
    public Mono<RestResponse> call(Connection connection, RestRequest request, final boolean fileDownload) {
        return FlatMapUtil.flatMapMono(
                        () -> this.applyConnectionDetails(
                                        connection,
                                        request.getUrl(),
                                        request.isIgnoreDefaultHeaders(),
                                        request.getTimeout())
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                                        connection.getName())),
                        tup -> {
                            HttpHeaders headers = tup.getT2();

                            if (request.getHeaders() != null
                                    && !request.getHeaders().isEmpty()) headers.addAll(request.getHeaders());

                            WebClientEncodingModes encodingMode = WebClientEncodingModes.valueOf(connection
                                    .getConnectionDetails()
                                    .getOrDefault(ENCODING_MODE, WebClientEncodingModes.TEMPLATE_AND_VALUES)
                                    .toString());

                            CustomUriBuilderFactory uriBuilderFactory =
                                    new CustomUriBuilderFactory(tup.getT1(), encodingMode);

                            WebClient.Builder webClientBuilder = WebClient.builder();
                            WebClient webClient = webClientBuilder
                                    .baseUrl(tup.getT1())
                                    .codecs(c -> c.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                                    .build();

                            WebClient.RequestBodySpec requestBuilder = webClient
                                    .mutate()
                                    .uriBuilderFactory(uriBuilderFactory)
                                    .build()
                                    .method(HttpMethod.valueOf(request.getMethod()))
                                    .uri(uriBuilder -> {
                                        uriBuilder = applyQueryParameters(
                                                uriBuilder, request.getQueryParameters(), encodingMode);
                                        return uriBuilder.build(request.getPathParameters());
                                    })
                                    .headers(h -> h.addAll(headers));

                            if (request.getPayload() == null
                                    && (request.getMethod().equalsIgnoreCase("GET")
                                            || request.getMethod().equalsIgnoreCase("DELETE")))
                                return requestBuilder.exchangeToMono(
                                        clientResponse -> handleResponse(clientResponse, tup.getT3(), fileDownload));

                            if (headers.getContentType() != null
                                    && headers.getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
                                MultipartBodyBuilder newPayload = getFormDataFromJson(request.getPayload());
                                return doRequestWithFormData(newPayload, requestBuilder, tup.getT3(), fileDownload);
                            }

                            if (headers.getContentType() != null
                                    && headers.getContentType()
                                            .isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
                                MultiValueMap<String, String> newPayload = getURLFormDataFromJson(request.getPayload());
                                return doRequestWithFormData(newPayload, requestBuilder, tup.getT3(), fileDownload);
                            }

                            return requestBuilder
                                    .bodyValue(
                                            !request.getPayload().isJsonNull()
                                                    ? JsonUtil.toMap(
                                                            request.getPayload().getAsJsonObject())
                                                    : Collections.emptyMap())
                                    .exchangeToMono(
                                            clientResponse -> handleResponse(clientResponse, tup.getT3(), fileDownload))
                                    .onErrorReturn(new RestResponse()
                                            .setStatus(HttpStatus.BAD_REQUEST.value())
                                            .setData("Url not found with the given" + " connection"));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BasicRestService.call"));
    }

    private MultipartBodyBuilder getFormDataFromJson(JsonElement payload) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        if (!payload.isJsonObject()) return builder;

        for (var x : payload.getAsJsonObject().entrySet()) {
            if (x.getValue().isJsonArray()) {
                for (JsonElement y : x.getValue().getAsJsonArray())
                    this.addFormData(builder, x.getKey(), y.getAsString());
            } else this.addFormData(builder, x.getKey(), x.getValue().getAsString());
        }

        return builder;
    }

    private void addFormData(MultipartBodyBuilder builder, String key, String value) {
        if (value.startsWith(ENCODED_TEXT_TAG_DATA)) {
            String[] parts = value.split(";");
            if (parts.length < 3) {
                builder.part(key, value);
                return;
            }

            if (parts[0].startsWith(ENCODED_TEXT_TAG_DATA)
                    && parts[1].startsWith(ENCODED_TEXT_TAG_NAME)
                    && parts[2].startsWith(ENCODED_TEXT_TAG_BASE)) {
                String type = parts[0].substring(ENCODED_TEXT_TAG_DATA.length());
                String name = parts[1].substring(ENCODED_TEXT_TAG_NAME.length());
                String base64 = parts[2].substring(ENCODED_TEXT_TAG_BASE.length());

                builder.part(key, decodeToFile(base64))
                        .contentType(MediaType.valueOf(type))
                        .filename(name);
                return;
            }
        }
        builder.part(key, value);
    }

    private MultiValueMap<String, String> getURLFormDataFromJson(JsonElement payload) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();

        if (!payload.isJsonObject()) return map;

        for (var x : payload.getAsJsonObject().entrySet())
            map.add(x.getKey(), x.getValue().toString());

        return map;
    }

    private Mono<RestResponse> doRequestWithFormData(
            MultipartBodyBuilder builder,
            WebClient.RequestBodySpec requestBuilder,
            Duration timeoutDuration,
            boolean fileDownload) {
        return requestBuilder
                .bodyValue(builder.build())
                .exchangeToMono(clientResponse -> handleResponse(clientResponse, timeoutDuration, fileDownload));
    }

    private Mono<RestResponse> doRequestWithFormData(
            MultiValueMap<String, String> builder,
            WebClient.RequestBodySpec requestBuilder,
            Duration timeoutDuration,
            boolean fileDownload) {
        return requestBuilder
                .bodyValue(builder)
                .exchangeToMono(clientResponse -> handleResponse(clientResponse, timeoutDuration, fileDownload));
    }

    private Mono<Tuple3<String, HttpHeaders, Duration>> applyConnectionDetails(
            Connection connection, String url, boolean ignoreDefaultHeaders, int timeout) {
        HttpHeaders headers = new HttpHeaders();

        Duration timeoutDuration = Duration.ofSeconds(timeout < 1 ? 300 : timeout);

        Map<String, Object> connectionDetails = connection.getConnectionDetails();

        if (connectionDetails == null || connectionDetails.isEmpty()) return Mono.empty();

        if (connectionDetails.get("timeout") instanceof Integer conTimeout)
            timeoutDuration = Duration.ofSeconds(timeout < 1 ? conTimeout : timeout);

        if (connectionDetails.containsKey("userName") && connectionDetails.containsKey("password")) {
            String userName = (String) connectionDetails.get("userName");
            String password = (String) connectionDetails.get("password");
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes());
            headers.set("Authorization", basicAuth);
        }

        if (connectionDetails.containsKey("defaultHeaders")
                && !ignoreDefaultHeaders
                && connectionDetails.get("defaultHeaders") instanceof Map<?, ?> dHeaders)
            dHeaders.entrySet().stream()
                    .filter(header -> header.getValue() != null)
                    .forEach(header -> headers.set(
                            header.getKey().toString(), header.getValue().toString()));

        if (connectionDetails.containsKey("baseUrl")) {
            String baseUrl = connectionDetails.get("baseUrl").toString();
            if (baseUrl.trim().endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/'));
            if (url.trim().startsWith("/")) url = url.substring(1);
            url = baseUrl + '/' + url;
        }

        return Mono.just(Tuples.of(url, headers, timeoutDuration));
    }

    private UriBuilder applyQueryParameters(
            UriBuilder uriBuilder, Map<String, String> queryParameters, WebClientEncodingModes encodingMode) {
        if (queryParameters != null && !queryParameters.isEmpty()) {
            queryParameters.forEach((key, value) -> uriBuilder.queryParam(
                    key,
                    encodingMode.equals(WebClientEncodingModes.MANUAL_VALUES_ENCODED)
                            ? URLEncoder.encode(value, StandardCharsets.UTF_8)
                            : value));
        }
        return uriBuilder;
    }

    private Mono<RestResponse> handleResponse(ClientResponse clientResponse, Duration timeout, boolean fileDownload) {
        HttpHeaders headers = clientResponse.headers().asHttpHeaders();
        MediaType contentType = headers.getContentType();

        RestResponse restResponse = new RestResponse();
        restResponse.setHeaders(clientResponse.headers().asHttpHeaders().toSingleValueMap());
        restResponse.setStatus(clientResponse.statusCode().value());

        if (contentType == null)
            return clientResponse
                    .bodyToMono(String.class)
                    .map(restResponse::setData)
                    .timeout(timeout)
                    .onErrorResume(throwable -> Mono.just(createErrorResponse(throwable)));

        if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return clientResponse
                    .bodyToMono(String.class)
                    .map(jsonData -> restResponse.setData(processJsonResponse(jsonData)))
                    .timeout(timeout)
                    .onErrorResume(throwable -> Mono.just(createErrorResponse(throwable)));
        } else if (fileDownload
                || headers.getContentDisposition().getFilename() != null
                || contentType.getType().equals(MimeTypeUtils.APPLICATION_OCTET_STREAM.getType())) {
            return clientResponse
                    .bodyToMono(byte[].class)
                    .map(binaryData -> processBinaryResponse(binaryData, restResponse))
                    .timeout(timeout)
                    .onErrorResume(throwable -> Mono.just(createErrorResponse(throwable)));
        }

        return clientResponse
                .bodyToMono(String.class)
                .map(restResponse::setData)
                .timeout(timeout)
                .onErrorResume(throwable -> Mono.just(createErrorResponse(throwable)));
    }

    private Object processJsonResponse(String jsonData) {
        JsonElement jsonElement = gson.fromJson(jsonData, JsonElement.class);
        if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive prim = jsonElement.getAsJsonPrimitive();
            if (prim.isNumber()) return prim.getAsNumber();
            else if (prim.isJsonNull()) return null;
            else if (prim.isBoolean()) return prim.getAsBoolean();
            else if (prim.isString()) return prim.getAsString();
        } else if (jsonElement.isJsonObject()) {
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            return gson.fromJson(jsonData, mapType);
        } else if (jsonElement.isJsonArray()) {
            Type arrayType = new TypeToken<List<Object>>() {}.getType();
            return gson.fromJson(jsonData, arrayType);
        }
        return null;
    }

    private RestResponse processBinaryResponse(byte[] binaryData, RestResponse restResponse) {
        restResponse.setData(binaryData);
        return restResponse;
    }

    private RestResponse createErrorResponse(Throwable throwable) {
        RestResponse errorResponse = new RestResponse();
        errorResponse.setStatus(500);
        errorResponse.setData("Error occurred: " + throwable.getMessage());
        return errorResponse;
    }
}
