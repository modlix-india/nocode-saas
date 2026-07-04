package com.modlix.saas.adzump.service.asset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;
import com.modlix.saas.adzump.model.asset.StoredFile;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * J16 — the thin, injectable blocking facade over the Modlix <b>files service</b> internal API. It is
 * the one place J16 knows the files wire (the {@code /api/files/internal/{resourceType}} upload +
 * {@code /file} download endpoints, gateway-gated so only the {@code clientCode} scope is passed). The
 * layers above ({@link AssetService}, the registrars) speak only in {@code byte[]} + {@link StoredFile}
 * and never touch a files-service model type — so every unit test <b>mocks this client</b> and no test
 * touches the files service.
 *
 * <p>Adzump stores no blobs of its own (J16 §2): the bytes live in the client's Modlix file space and
 * this facade is the read/write bridge to them. It reuses the existing files service rather than
 * introducing a new blob store.
 *
 * <p><b>Live path (deferred, P4.5):</b> the real outbound calls only fire against a running files
 * service. The base URL / resource type are pinned in config and default to the discovery service name;
 * the exact internal contract is proven at the P4.5 integration gate. Errors are mapped to a
 * {@link GenericException} via {@link AdzumpMessageResourceService#FILES_API_ERROR} so no
 * {@code RestClient} type leaks above the facade.
 */
@Component
public class AdzumpFilesClient {

    /** Where J16 media lands under the client's file space; kept out of the way of user uploads. */
    private static final String BASE_PATH = "/adzump/assets";

    private final RestClient http;
    private final String resourceType; // "secured" (default) | "static"
    private final AdzumpMessageResourceService msgService;

    public AdzumpFilesClient(
            @Value("${adzump.files.base-url:http://files}") String baseUrl,
            @Value("${adzump.files.resource-type:secured}") String resourceType,
            AdzumpMessageResourceService msgService) {

        this.http = RestClient.builder().baseUrl(stripTrailingSlash(baseUrl)).build();
        this.resourceType = resourceType;
        this.msgService = msgService;
    }

    /**
     * Stores {@code content} in {@code clientCode}'s Modlix file space and returns the resulting
     * files-service reference ({@code filePath} → {@link StoredFile#fileKey()} + {@code url}). The
     * files service replies with a {@code FileDetail}; only the two fields J16 needs are bound.
     */
    public StoredFile store(String clientCode, AdzumpAssetKind kind, String fileName, byte[] content,
            String contentType) {
        try {
            FileDetailView detail = this.http.post()
                    .uri(builder -> builder.path("/api/files/internal/{resourceType}")
                            .queryParam("clientCode", clientCode)
                            .queryParam("filePath", folderFor(kind))
                            .queryParam("fileName", fileName)
                            .build(this.resourceType))
                    .contentType(mediaType(contentType))
                    .body(content)
                    .retrieve()
                    .body(FileDetailView.class);

            if (detail == null || detail.filePath() == null || detail.filePath().isBlank())
                return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                        AdzumpMessageResourceService.FILES_API_ERROR, "no file reference returned from upload");

            return new StoredFile(detail.filePath(), detail.url());
        } catch (RestClientResponseException e) {
            throw apiError(e);
        } catch (RestClientException e) {
            throw transportError(e);
        }
    }

    /**
     * Fetches the raw bytes for a stored {@code fileKey} (the files-service {@code filePath}) so a
     * registrar can hand them to a platform's asset-upload endpoint.
     */
    public byte[] fetch(String clientCode, String fileKey) {
        try {
            byte[] body = this.http.get()
                    .uri(builder -> builder.path("/api/files/internal/{resourceType}/file")
                            .queryParam("clientCode", clientCode)
                            .queryParam("filePath", fileKey)
                            .build(this.resourceType))
                    .retrieve()
                    .body(byte[].class);

            if (body == null || body.length == 0)
                return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                        AdzumpMessageResourceService.FILES_API_ERROR, "empty file for key " + fileKey);

            return body;
        } catch (RestClientResponseException e) {
            throw apiError(e);
        } catch (RestClientException e) {
            throw transportError(e);
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    private static String folderFor(AdzumpAssetKind kind) {
        return BASE_PATH + "/" + (kind == null ? "misc" : kind.getLiteral().toLowerCase());
    }

    private static MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank())
            return MediaType.APPLICATION_OCTET_STREAM;
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private GenericException apiError(RestClientResponseException e) {
        String raw = e.getResponseBodyAsString();
        return this.msgService.nonReactiveMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg, e),
                AdzumpMessageResourceService.FILES_API_ERROR,
                raw == null || raw.isBlank() ? e.getStatusText() : raw);
    }

    private GenericException transportError(RestClientException e) {
        return this.msgService.nonReactiveMessage(
                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg, e),
                AdzumpMessageResourceService.FILES_API_ERROR,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    /** The subset of the files-service {@code FileDetail} J16 binds; unknown fields are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FileDetailView(String filePath, String url) {
    }
}
