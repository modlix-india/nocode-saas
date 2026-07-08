package com.modlix.saas.adzump.service.asset;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dao.AssetDao;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;
import com.modlix.saas.adzump.model.asset.Asset;
import com.modlix.saas.adzump.model.asset.PlatformAssetId;
import com.modlix.saas.adzump.model.asset.RegStatus;
import com.modlix.saas.adzump.model.asset.StoredFile;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J16 — assets / media. Owns the asset CRUD and the <b>distinct logic</b>: the bridge between Modlix
 * file storage and each platform's asset library. Blocking / imperative (built like {@code files} on
 * commons2-jooq + commons2-security); no Reactor.
 *
 * <p><b>Upload / generate</b> store the media once in the client's Modlix file space (via
 * {@link AdzumpFilesClient}) and create the {@code adzump_asset} row (source of truth = the file ref).
 *
 * <p><b>{@link #ensureRegistered}</b> is the load-bearing method — lazy, idempotent, async-aware:
 * <ul>
 * <li><b>Lazy:</b> an asset is registered on a platform the first time a creative for that platform
 *     needs it (called by J7 compile / J8 launch), not eagerly on upload.</li>
 * <li><b>Idempotent:</b> a {@code READY} cache hit returns the stored id with no re-upload; only a
 *     miss / {@code FAILED} triggers an upload.</li>
 * <li><b>Async-aware:</b> a Meta video comes back {@code PROCESSING}; a subsequent call polls the
 *     registrar (no re-upload) and flips to {@code READY} once transcoded. J8 launch must block on
 *     {@code READY} before attaching the video to an ad — the status is surfaced to the caller.</li>
 * </ul>
 *
 * <p><b>Tenant model (mirrors {@code AutonomyConfigService} / {@code FilesAccessPathService}).</b>
 * clientCode is never read from a request body. Mutations resolve an effective client code (optional
 * {@code targetClientCode}, defaulting to the caller's own client; a differing target is allowed only
 * for the system client or a managing client administering it). Reads carry no {@code @PreAuthorize}
 * and are tenant-scoped at runtime: {@link #get} / {@link #ensureRegistered} re-run the managed-client
 * gate on the fetched row; {@link #list} scopes to the resolved effective client.
 */
@Service
public class AssetService {

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String ASSET = "asset";
    private static final String ID = "id";
    private static final String CLIENT_CODE = "clientCode";

    private final AssetDao dao;
    private final AdzumpFilesClient filesClient;
    private final ConnectionService connections;
    private final AssetGenerator generator;
    private final Map<Platform, AssetRegistrar> registrars;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public AssetService(AssetDao dao, AdzumpFilesClient filesClient, ConnectionService connections,
            AssetGenerator generator, List<AssetRegistrar> registrars,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {

        this.dao = dao;
        this.filesClient = filesClient;
        this.connections = connections;
        this.generator = generator;
        this.securityService = securityService;
        this.msgService = msgService;

        Map<Platform, AssetRegistrar> byPlatform = new EnumMap<>(Platform.class);
        for (AssetRegistrar registrar : registrars)
            byPlatform.put(registrar.platform(), registrar);
        this.registrars = byPlatform;
    }

    // =====================================================================================
    // Upload / generate — store the Modlix file ref + row
    // =====================================================================================

    /**
     * Stores an uploaded medium in the effective client's Modlix file space and creates its asset row.
     * The bytes are the source of truth; registration on a platform is deferred to
     * {@link #ensureRegistered}.
     */
    @PreAuthorize(EDIT)
    public Asset upload(AdzumpAssetKind kind, String fileName, byte[] content, String contentType,
            JsonNode attributes, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (kind == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "kind");
        if (fileName == null || fileName.isBlank())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "fileName");
        if (content == null || content.length == 0)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "content");

        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);

        StoredFile stored = this.filesClient.store(clientCode, kind, fileName, content, contentType);

        return this.persistNew(clientCode, kind, stored, attributes, ca);
    }

    /**
     * Orchestrates generation (J16 §5.3): delegate to the {@link AssetGenerator} for the bytes, then
     * store + create the row on the same path as {@link #upload}, so a generated asset is classified,
     * attributed and registered identically to an uploaded one. The generation backend is a deferred
     * seam this slice.
     */
    @PreAuthorize(EDIT)
    public Asset generate(AdzumpAssetKind kind, String brief, JsonNode attributes, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (kind == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "kind");
        if (brief == null || brief.isBlank())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "brief");

        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);

        GeneratedAsset generated = this.generator.generate(kind, brief, clientCode);

        StoredFile stored = this.filesClient.store(clientCode, kind, generated.fileName(),
                generated.content(), generated.contentType());

        JsonNode mergedAttributes = attributes != null ? attributes : generated.attributes();
        return this.persistNew(clientCode, kind, stored, mergedAttributes, ca);
    }

    // =====================================================================================
    // Reads — tenant-scoped, no @PreAuthorize
    // =====================================================================================

    /** Fetches one asset by id, enforcing the managed-client tenant gate on the fetched row. */
    public Asset get(ULong assetId) {
        return this.readTenantScoped(assetId);
    }

    /** Lists the assets of the effective client (the caller's own, or a managed sub-client's). */
    public List<Asset> list(String targetClientCode) {
        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);
        return this.dao.readAll(FilterCondition.make(CLIENT_CODE, clientCode));
    }

    // =====================================================================================
    // Registration — lazy, idempotent, async-aware (the distinct logic)
    // =====================================================================================

    /**
     * Ensures {@code assetId} is registered on {@code platform} and returns the platform id + status.
     * Idempotent on a {@code READY} cache hit (no re-upload); for a still-{@code PROCESSING} video it
     * polls the platform (again, no re-upload) and only then flips to {@code READY}. The tenant gate is
     * re-run on the fetched row, so a caller who cannot manage the asset's client is refused.
     */
    @PreAuthorize(EDIT)
    public PlatformAssetId ensureRegistered(ULong assetId, Platform platform) {

        if (platform == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "platform");

        Asset asset = this.readTenantScoped(assetId);

        if (asset.getFileKey() == null || asset.getFileKey().isBlank())
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.UNPROCESSABLE_ENTITY, msg),
                    AdzumpMessageResourceService.ASSET_FILE_MISSING, assetId);

        AssetRegistrar registrar = this.registrars.get(platform);
        if (registrar == null)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.PLATFORM_NOT_AVAILABLE, platform);

        // A fresh EnumMap seeded from the persisted map. Built with putAll rather than the
        // EnumMap(Map) copy constructor because that constructor throws on a non-EnumMap that is
        // empty (it cannot infer the enum class) — and a DB read yields a LinkedHashMap, possibly {}.
        Map<Platform, PlatformAssetId> ids = new EnumMap<>(Platform.class);
        if (asset.getPlatformIds() != null)
            ids.putAll(asset.getPlatformIds());
        PlatformAssetId current = ids.get(platform);

        // Idempotent cache hit: already READY -> return cached id, resolve no token, do NOT re-upload.
        if (current != null && current.status() == RegStatus.READY)
            return current;

        Token token = toToken(this.connections.resolve(platform));

        PlatformAssetId result;
        if (current != null && current.status() == RegStatus.PROCESSING
                && current.id() != null && !current.id().isBlank())
            // Async video: poll status, never re-upload a PROCESSING asset.
            result = registrar.checkStatus(asset, current, token);
        else
            // Miss or FAILED: upload / register.
            result = registrar.register(asset, token);

        // Cache the outcome so the next call is idempotent; skip the write when nothing changed
        // (e.g. a poll that is still PROCESSING).
        if (!result.equals(current)) {
            ids.put(platform, result);
            asset.setPlatformIds(ids);
            ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
            if (ca.getUser() != null)
                asset.setUpdatedBy(ULong.valueOf(ca.getUser().getId()));
            this.dao.update(asset);
        }

        return result;
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private Asset persistNew(String clientCode, AdzumpAssetKind kind, StoredFile stored, JsonNode attributes,
            ContextAuthentication ca) {

        Asset asset = new Asset()
                .setClientCode(clientCode)
                .setKind(kind)
                .setFileKey(stored.fileKey())
                .setUrl(stored.url())
                .setAttributes(attributes);

        if (ca.getUser() != null)
            asset.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

        return this.dao.create(asset);
    }

    /**
     * Reads an asset by id or the domain-specific {@code ASSET_NOT_FOUND}, then enforces the
     * managed-client tenant gate (own client or a client the caller manages), mirroring
     * {@code CampaignPlanService.read}.
     */
    private Asset readTenantScoped(ULong assetId) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        Asset asset = this.findById(assetId);
        if (asset == null)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    AdzumpMessageResourceService.ASSET_NOT_FOUND, assetId);

        if (!this.isClientBeingManaged(ca.getLoggedInFromClientCode(), asset.getClientCode()))
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, ASSET);

        return asset;
    }

    private Asset findById(ULong id) {
        List<Asset> rows = this.dao.readAll(FilterCondition.make(ID, id));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Resolves the effective client code for a write/read, mirroring
     * {@code AutonomyConfigService.resolveEffectiveClientCode}. Defaults to the caller's own client; a
     * differing target is allowed only for the system client or a managing client administering it.
     */
    private String resolveEffectiveClientCode(String targetClientCode, ContextAuthentication ca) {

        String own = ca.getLoggedInFromClientCode();

        if (targetClientCode == null || targetClientCode.isBlank()
                || StringUtil.safeEquals(targetClientCode.trim(), own))
            return own;

        String target = targetClientCode.trim();
        BigInteger targetClientId = this.securityService.getClientIdByCode(target);

        boolean allowed = ca.isSystemClient()
                || Boolean.TRUE.equals(this.securityService.isUserClientManageClient(ca.getUrlAppCode(),
                        ca.getUser().getId(), ca.getUser().getClientId(), targetClientId));

        if (!allowed)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CLIENT);

        return target;
    }

    private boolean isClientBeingManaged(String managingClientCode, String clientCode) {
        if (StringUtil.safeEquals(managingClientCode, clientCode))
            return true;
        return Boolean.TRUE.equals(this.securityService.doesClientManageClientCode(managingClientCode, clientCode));
    }

    private static Token toToken(PlatformCredential credential) {
        Map<String, String> attributes = credential.getAttributes() == null ? Map.of() : credential.getAttributes();
        // Google needs an MCC / login-customer context alongside the token; Meta leaves it null.
        String loginCustomerId = attributes.get("loginCustomerId");
        return new Token(credential.getAccessToken(), credential.getAccountId(), loginCustomerId, attributes);
    }
}
