package com.modlix.saas.adzump.service.asset;

import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;
import com.modlix.saas.adzump.model.asset.Asset;
import com.modlix.saas.adzump.model.asset.PlatformAssetId;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.platform.meta.MetaGraphClient;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * J16 §5.2 — the Meta registrar. It registers a stored asset with the Meta ad account and returns the
 * platform-real id a creative must reference:
 * <ul>
 * <li><b>Image / logo</b> → {@code POST act_&lt;id&gt;/adimages} with the base64 bytes; Meta replies
 *     with a permanent {@code hash} synchronously, so the result is {@code READY} at once.</li>
 * <li><b>Video</b> → {@code POST act_&lt;id&gt;/advideos}; Meta returns a {@code video_id} immediately
 *     but the video is not usable until it finishes transcoding. So {@link #register} returns
 *     {@code PROCESSING}, and {@link #checkStatus} polls {@code GET &lt;video_id&gt;?fields=status}
 *     until {@code video_status == ready}. J8 launch blocks on {@code READY} before attaching it.</li>
 * </ul>
 *
 * <p>The bytes come from the Modlix files service via {@link AdzumpFilesClient} (adzump stores no
 * blobs). The wire is {@link MetaGraphClient}; both are <b>mocked in every test</b> — no live account.
 */
@Component
public class MetaAssetRegistrar implements AssetRegistrar {

    private static final String STATUS = "status";

    private final MetaGraphClient graph;
    private final AdzumpFilesClient files;
    private final AdzumpMessageResourceService msgService;

    public MetaAssetRegistrar(MetaGraphClient graph, AdzumpFilesClient files,
            AdzumpMessageResourceService msgService) {
        this.graph = graph;
        this.files = files;
        this.msgService = msgService;
    }

    @Override
    public Platform platform() {
        return Platform.META;
    }

    @Override
    public PlatformAssetId register(Asset asset, Token token) {
        String account = accountNode(token);
        AdzumpAssetKind kind = asset.getKind();
        if (kind == AdzumpAssetKind.VIDEO)
            return registerVideo(asset, token, account);
        // IMAGE and LOGO are both plain images on Meta.
        return registerImage(asset, token, account);
    }

    @Override
    public PlatformAssetId checkStatus(Asset asset, PlatformAssetId current, Token token) {
        // Only a Meta video is async; images are already READY. Nothing to poll otherwise.
        if (current == null || current.id() == null || current.id().isBlank()
                || asset.getKind() != AdzumpAssetKind.VIDEO)
            return current;

        JsonNode response = this.graph.get(token, current.id(), Map.of("fields", STATUS));
        String videoStatus = response == null ? ""
                : response.path(STATUS).path("video_status").asText("");

        return switch (videoStatus.toLowerCase()) {
            case "ready" -> PlatformAssetId.ready(current.id());
            case "error", "failed" -> PlatformAssetId.failed(current.id());
            default -> current; // still processing
        };
    }

    // --- per-kind registration ------------------------------------------------------------------

    private PlatformAssetId registerImage(Asset asset, Token token, String account) {
        byte[] bytes = this.files.fetch(asset.getClientCode(), asset.getFileKey());
        String base64 = Base64.getEncoder().encodeToString(bytes);

        JsonNode response = this.graph.post(token, account + "/adimages", Map.of("bytes", base64));

        String hash = extractImageHash(response);
        return PlatformAssetId.ready(hash);
    }

    private PlatformAssetId registerVideo(Asset asset, Token token, String account) {
        // Meta accepts a publicly-fetchable file_url for video; the source-of-truth URL is the Modlix
        // files ref. (Bytes-upload is the P4.5 alternative when the url is not reachable by Meta.)
        String fileUrl = asset.getUrl();
        if (fileUrl == null || fileUrl.isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.UNPROCESSABLE_ENTITY, msg),
                    AdzumpMessageResourceService.ASSET_FILE_MISSING, asset.getId());

        JsonNode response = this.graph.post(token, account + "/advideos", Map.of("file_url", fileUrl));
        String videoId = extractId(response, account + "/advideos");
        // Async: usable only after transcode. Caller polls checkStatus() until READY.
        return PlatformAssetId.processing(videoId);
    }

    // --- response parsing -----------------------------------------------------------------------

    /** {@code /adimages} replies {@code {"images":{"<name>":{"hash":"...","url":"..."}}}}. */
    private String extractImageHash(JsonNode response) {
        JsonNode images = response == null ? null : response.get("images");
        if (images != null && images.isObject()) {
            Iterator<JsonNode> it = images.elements();
            if (it.hasNext()) {
                JsonNode hash = it.next().get("hash");
                if (hash != null && !hash.isNull() && !hash.asText().isBlank())
                    return hash.asText();
            }
        }
        return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                AdzumpMessageResourceService.META_API_ERROR, "no image hash returned from adimages");
    }

    private String extractId(JsonNode response, String edge) {
        JsonNode id = response == null ? null : response.get("id");
        if (id == null || id.isNull() || id.asText().isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                    AdzumpMessageResourceService.META_API_ERROR, "no id returned from " + edge);
        return id.asText();
    }

    /** The account node ({@code act_...}) writes are scoped to, from the J2 token. */
    private String accountNode(Token token) {
        String accountId = token == null ? null : token.accountId();
        if (accountId == null || accountId.isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "accountId");
        return accountId.startsWith("act_") ? accountId : "act_" + accountId;
    }
}
