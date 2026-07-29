package com.modlix.saas.adzump.platform.meta;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * J3 — the thin blocking HTTP facade over the Meta Graph (Marketing) API. Everything above it
 * (MetaPlatform / MetaLifecycle / MetaInsightsReader) speaks in terms of edges + parameter maps and
 * reads back a {@link JsonNode}; only this class knows the wire (URL, auth header, form encoding,
 * error shape). That seam is deliberate: the higher layers are unit-tested by <b>mocking this
 * client</b>, so no test ever touches a live account.
 *
 * <p>
 * <b>Auth</b>: the J2-resolved {@link Token#accessToken()} rides in the {@code Authorization: Bearer}
 * header (Graph accepts this in addition to the legacy {@code access_token} param), so the token is
 * never placed in a logged URL. <b>Version</b> is pinned in config
 * ({@code adzump.meta.api-version}, default {@code v22.0} — a current documented Graph version) and
 * lives in exactly one place, since version drift on deprecation is the top Meta risk. <b>Errors</b>:
 * any 4xx/5xx is mapped to a {@link GenericException} carrying the Graph {@code error.message} via
 * {@link AdzumpMessageResourceService#META_API_ERROR}.
 * </p>
 *
 * <p>
 * <b>Live path</b>: the actual outbound calls only fire when a real Meta account is connected; that
 * step is deferred + flag-gated (needs Kiran's connected account). The client code below is the real
 * implementation, but every test mocks it.
 * </p>
 */
@Component
public class MetaGraphClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient http;
    private final String apiVersion;
    private final AdzumpMessageResourceService msgService;

    public MetaGraphClient(
            @Value("${adzump.meta.base-url:https://graph.facebook.com}") String baseUrl,
            @Value("${adzump.meta.api-version:v22.0}") String apiVersion,
            AdzumpMessageResourceService msgService) {

        this.apiVersion = apiVersion;
        this.msgService = msgService;
        // baseUrl + pinned version → the single Graph root every edge is resolved against.
        String root = stripTrailingSlash(baseUrl) + "/" + apiVersion;
        this.http = RestClient.builder().baseUrl(root).build();
    }

    /** The pinned Graph API version (surfaced for health/diagnostics). */
    public String apiVersion() {
        return this.apiVersion;
    }

    // --- low-level verbs (the mock seam) --------------------------------------------------------

    /**
     * GET an edge. {@code edge} is the path after the version (e.g. {@code "me/businesses"},
     * {@code "act_123/adspixels"}); {@code params} are appended as query parameters (complex values
     * must already be JSON-encoded strings by the caller, per Graph convention).
     */
    public JsonNode get(Token token, String edge, Map<String, String> params) {
        try {
            return this.http.get()
                    .uri(builder -> {
                        builder.path("/" + edge);
                        if (params != null)
                            params.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw apiError(e);
        } catch (RestClientException e) {
            throw transportError(e);
        }
    }

    /**
     * POST to an edge (create / update). The body is form-encoded (the Graph convention for
     * Marketing-API writes): scalar values go verbatim, nested objects/arrays are JSON-stringified.
     * Returns the created/updated node (Graph replies {@code {"id": "..."}} on create).
     */
    public JsonNode post(Token token, String edge, Map<String, ?> body) {
        MultiValueMap<String, String> form = encodeForm(body);
        try {
            return this.http.post()
                    .uri("/" + edge)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw apiError(e);
        } catch (RestClientException e) {
            throw transportError(e);
        }
    }

    /** DELETE a node by id — used only by {@code MetaLifecycle}'s do-no-harm rollback. */
    public void delete(Token token, String node) {
        try {
            this.http.method(HttpMethod.DELETE)
                    .uri("/" + node)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw apiError(e);
        } catch (RestClientException e) {
            throw transportError(e);
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    private static String bearer(Token token) {
        return "Bearer " + (token == null ? "" : token.accessToken());
    }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static MultiValueMap<String, String> encodeForm(Map<String, ?> body) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (body == null)
            return form;
        for (Map.Entry<String, ?> entry : body.entrySet()) {
            if (entry.getValue() == null)
                continue;
            form.add(entry.getKey(), formValue(entry.getValue()));
        }
        return form;
    }

    /** Scalars pass through as text; objects/arrays/JsonNode containers are JSON-encoded strings. */
    private static String formValue(Object value) {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean)
            return value.toString();
        if (value instanceof JsonNode node)
            return node.isValueNode() ? node.asText() : node.toString();
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode Meta request field", e);
        }
    }

    private GenericException apiError(RestClientResponseException e) {
        return this.msgService.nonReactiveMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg, e),
                AdzumpMessageResourceService.META_API_ERROR, extractMetaError(e));
    }

    private GenericException transportError(RestClientException e) {
        return this.msgService.nonReactiveMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg, e),
                AdzumpMessageResourceService.META_API_ERROR,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    /** Pulls Graph's {@code error.message} out of the response body, falling back to the raw text. */
    private static String extractMetaError(RestClientResponseException e) {
        String raw = e.getResponseBodyAsString();
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode error = MAPPER.readTree(raw).get("error");
                if (error != null && error.get("message") != null)
                    return error.get("message").asText();
            } catch (JsonProcessingException ignored) {
                // fall through to the raw body / status text
            }
            return raw;
        }
        return e.getStatusText();
    }
}
