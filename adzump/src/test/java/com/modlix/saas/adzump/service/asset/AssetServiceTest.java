package com.modlix.saas.adzump.service.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.ads.googleads.v24.services.MutateAssetResult;
import com.google.ads.googleads.v24.services.MutateGoogleAdsResponse;
import com.google.ads.googleads.v24.services.MutateOperationResponse;
import com.modlix.saas.adzump.dao.AssetDao;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;
import com.modlix.saas.adzump.model.asset.Asset;
import com.modlix.saas.adzump.model.asset.PlatformAssetId;
import com.modlix.saas.adzump.model.asset.RegStatus;
import com.modlix.saas.adzump.model.asset.StoredFile;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.platform.google.GoogleAdsClientFacade;
import com.modlix.saas.adzump.platform.meta.MetaGraphClient;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * J16 asset-registration unit tests, run entirely offline: the <b>real</b> {@link MetaAssetRegistrar}
 * and {@link GoogleAssetRegistrar} wrap <b>mocked</b> low-level clients ({@link MetaGraphClient},
 * {@link GoogleAdsClientFacade}, {@link AdzumpFilesClient}) so no live account, HTTP or gRPC is
 * touched. A real {@link AdzumpMessageResourceService} raises the real {@link GenericException}s; the
 * DAO / connections / security collaborators are mocked.
 *
 * <p>Covers the J16 exit + the task's required cases: an idempotent {@code READY} cache hit that does
 * not re-upload, the Meta video {@code PROCESSING → READY} gating (poll, never re-upload), a
 * missing-file failure that leaves no partial cache, a tenant deny, plus the Meta-image-hash and
 * Google-resource-name happy paths.
 */
class AssetServiceTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();
    private static final ObjectMapper M = new ObjectMapper();
    private static final ULong ASSET_ID = ULong.valueOf(42);
    private static final String OWN = "CLI0";

    private MetaGraphClient graph;
    private GoogleAdsClientFacade gAds;
    private AdzumpFilesClient files;
    private AssetDao dao;
    private ConnectionService connections;
    private AssetGenerator generator;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;

    private AssetService service;

    @BeforeEach
    void setUp() {
        this.graph = mock(MetaGraphClient.class);
        this.gAds = mock(GoogleAdsClientFacade.class);
        this.files = mock(AdzumpFilesClient.class);
        this.dao = mock(AssetDao.class);
        this.connections = mock(ConnectionService.class);
        this.generator = mock(AssetGenerator.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn(OWN);

        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);

        MetaAssetRegistrar meta = new MetaAssetRegistrar(this.graph, this.files, MSG);
        GoogleAssetRegistrar google = new GoogleAssetRegistrar(this.gAds, this.files, MSG);
        this.service = new AssetService(this.dao, this.files, this.connections, this.generator,
                List.of(meta, google), this.security, MSG);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    // =====================================================================================
    // Registration — the distinct logic
    // =====================================================================================

    @Test
    void idempotentCacheHit_returnsCached_andNeverReuploads() {

        Map<Platform, PlatformAssetId> ids = new EnumMap<>(Platform.class);
        ids.put(Platform.META, PlatformAssetId.ready("hash_ready"));
        when(this.dao.readAll(any(AbstractCondition.class))).thenReturn(List.of(asset(AdzumpAssetKind.IMAGE, OWN, ids)));

        PlatformAssetId result = this.service.ensureRegistered(ASSET_ID, Platform.META);

        assertEquals(RegStatus.READY, result.status());
        assertEquals("hash_ready", result.id());

        // No token resolved, no platform call, no re-persist: a READY hit short-circuits everything.
        verify(this.connections, never()).resolve(any());
        verifyNoInteractions(this.graph);
        verify(this.files, never()).fetch(anyString(), anyString());
        verify(this.dao, never()).update(any(Asset.class));
    }

    @Test
    void metaVideo_processingThenReady_gatesWithoutReupload() {

        Asset asset = asset(AdzumpAssetKind.VIDEO, OWN, null);
        when(this.dao.readAll(any(AbstractCondition.class))).thenReturn(List.of(asset));
        when(this.connections.resolve(Platform.META)).thenReturn(credential("act_123", Map.of()));

        // Phase 1: upload returns a video_id immediately, but the video is not yet usable → PROCESSING.
        when(this.graph.post(any(), eq("act_123/advideos"), any())).thenReturn(node().put("id", "vid_1"));

        PlatformAssetId first = this.service.ensureRegistered(ASSET_ID, Platform.META);
        assertEquals(RegStatus.PROCESSING, first.status());
        assertEquals("vid_1", first.id());

        // The PROCESSING id is cached (so the next call polls, not re-uploads).
        ArgumentCaptor<Asset> cap1 = ArgumentCaptor.forClass(Asset.class);
        verify(this.dao).update(cap1.capture());
        assertEquals(RegStatus.PROCESSING, cap1.getValue().getPlatformIds().get(Platform.META).status());

        // Phase 2: the video finished transcoding → the poll flips it to READY, no second upload.
        ObjectNode status = node();
        status.putObject("status").put("video_status", "ready");
        when(this.graph.get(any(), eq("vid_1"), any())).thenReturn(status);

        PlatformAssetId second = this.service.ensureRegistered(ASSET_ID, Platform.META);
        assertEquals(RegStatus.READY, second.status());
        assertEquals("vid_1", second.id());

        verify(this.graph, times(1)).post(any(), eq("act_123/advideos"), any()); // never re-uploaded
        verify(this.graph, times(1)).get(any(), eq("vid_1"), any());
        verify(this.files, never()).fetch(anyString(), anyString()); // video uses file_url, not bytes
    }

    @Test
    void missingFile_registrationFails_andLeavesNoPartialCache() {

        when(this.dao.readAll(any(AbstractCondition.class)))
                .thenReturn(List.of(asset(AdzumpAssetKind.IMAGE, OWN, null)));
        when(this.connections.resolve(Platform.META)).thenReturn(credential("act_123", Map.of()));
        // The backing file is gone from storage: fetch fails before any upload.
        when(this.files.fetch(eq(OWN), anyString()))
                .thenThrow(new GenericException(org.springframework.http.HttpStatus.NOT_FOUND, "file gone"));

        assertThrows(GenericException.class, () -> this.service.ensureRegistered(ASSET_ID, Platform.META));

        verify(this.graph, never()).post(any(), anyString(), any()); // never reached
        verify(this.dao, never()).update(any(Asset.class)); // nothing cached on failure
    }

    @Test
    void tenantDeny_foreignClientTheCallerCannotManage_forbidden() {

        // The asset belongs to a client the caller neither owns nor manages.
        when(this.dao.readAll(any(AbstractCondition.class)))
                .thenReturn(List.of(asset(AdzumpAssetKind.IMAGE, "OTHER", null)));
        when(this.security.doesClientManageClientCode(OWN, "OTHER")).thenReturn(false);

        assertThrows(GenericException.class, () -> this.service.ensureRegistered(ASSET_ID, Platform.META));

        // Denied at the row tenant gate, before any token / platform call.
        verify(this.connections, never()).resolve(any());
        verifyNoInteractions(this.graph);
        verify(this.dao, never()).update(any(Asset.class));
    }

    @Test
    void assetNotFound_raisesAssetNotFound() {
        when(this.dao.readAll(any(AbstractCondition.class))).thenReturn(List.of());
        assertThrows(GenericException.class, () -> this.service.ensureRegistered(ASSET_ID, Platform.META));
        verify(this.connections, never()).resolve(any());
    }

    @Test
    void metaImage_registersHash_ready_andCaches() {

        Asset asset = asset(AdzumpAssetKind.IMAGE, OWN, null);
        when(this.dao.readAll(any(AbstractCondition.class))).thenReturn(List.of(asset));
        when(this.connections.resolve(Platform.META)).thenReturn(credential("act_123", Map.of()));
        when(this.files.fetch(eq(OWN), anyString())).thenReturn(new byte[] { 1, 2, 3 });

        ObjectNode resp = node();
        ObjectNode img = resp.putObject("images").putObject("source");
        img.put("hash", "img_hash").put("url", "https://cdn/x");
        when(this.graph.post(any(), eq("act_123/adimages"), any())).thenReturn(resp);

        PlatformAssetId result = this.service.ensureRegistered(ASSET_ID, Platform.META);

        assertEquals(RegStatus.READY, result.status());
        assertEquals("img_hash", result.id());

        ArgumentCaptor<Asset> cap = ArgumentCaptor.forClass(Asset.class);
        verify(this.dao).update(cap.capture());
        assertEquals("img_hash", cap.getValue().getPlatformIds().get(Platform.META).id());
    }

    @Test
    void googleImage_registersAssetResource_ready() {

        Asset asset = asset(AdzumpAssetKind.IMAGE, OWN, null);
        when(this.dao.readAll(any(AbstractCondition.class))).thenReturn(List.of(asset));
        when(this.connections.resolve(Platform.GOOGLE))
                .thenReturn(credential("1234567890", Map.of("loginCustomerId", "999")));
        when(this.files.fetch(eq(OWN), anyString())).thenReturn(new byte[] { 4, 5, 6 });

        String resourceName = "customers/1234567890/assets/55";
        when(this.gAds.mutate(any(), eq("1234567890"), any())).thenReturn(assetResponse(resourceName));

        PlatformAssetId result = this.service.ensureRegistered(ASSET_ID, Platform.GOOGLE);

        assertEquals(RegStatus.READY, result.status());
        assertEquals(resourceName, result.id());

        ArgumentCaptor<Asset> cap = ArgumentCaptor.forClass(Asset.class);
        verify(this.dao).update(cap.capture());
        assertEquals(resourceName, cap.getValue().getPlatformIds().get(Platform.GOOGLE).id());
    }

    @Test
    void googleVideo_unsupported() {
        when(this.dao.readAll(any(AbstractCondition.class)))
                .thenReturn(List.of(asset(AdzumpAssetKind.VIDEO, OWN, null)));
        when(this.connections.resolve(Platform.GOOGLE))
                .thenReturn(credential("1234567890", Map.of("loginCustomerId", "999")));

        assertThrows(GenericException.class, () -> this.service.ensureRegistered(ASSET_ID, Platform.GOOGLE));

        verifyNoInteractions(this.gAds);
        verify(this.dao, never()).update(any(Asset.class));
    }

    // =====================================================================================
    // Upload — store the Modlix file ref + row
    // =====================================================================================

    @Test
    void upload_storesFileAndCreatesRow() {

        when(this.files.store(eq(OWN), eq(AdzumpAssetKind.IMAGE), eq("hero.png"), any(), eq("image/png")))
                .thenReturn(new StoredFile("adzump/assets/image/hero.png", "https://cdn/hero.png"));
        when(this.dao.create(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            a.setId(ASSET_ID);
            return a;
        });

        Asset result = this.service.upload(AdzumpAssetKind.IMAGE, "hero.png", new byte[] { 9 }, "image/png", null, null);

        assertEquals("adzump/assets/image/hero.png", result.getFileKey());
        assertEquals(OWN, result.getClientCode());
        assertEquals(AdzumpAssetKind.IMAGE, result.getKind());

        ArgumentCaptor<Asset> cap = ArgumentCaptor.forClass(Asset.class);
        verify(this.dao).create(cap.capture());
        assertEquals("https://cdn/hero.png", cap.getValue().getUrl());
    }

    @Test
    void upload_missingContent_rejected() {
        assertThrows(GenericException.class,
                () -> this.service.upload(AdzumpAssetKind.IMAGE, "hero.png", new byte[0], "image/png", null, null));
        verifyNoInteractions(this.files);
        verify(this.dao, never()).create(any(Asset.class));
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private static Asset asset(AdzumpAssetKind kind, String clientCode, Map<Platform, PlatformAssetId> platformIds) {
        Asset a = new Asset()
                .setClientCode(clientCode)
                .setKind(kind)
                .setFileKey("adzump/assets/x")
                .setUrl("https://files/x")
                .setPlatformIds(platformIds);
        a.setId(ASSET_ID);
        return a;
    }

    private static PlatformCredential credential(String accountId, Map<String, String> attributes) {
        return new PlatformCredential()
                .setAccessToken("tok")
                .setAccountId(accountId)
                .setAttributes(attributes);
    }

    private static ObjectNode node() {
        return M.createObjectNode();
    }

    private static MutateGoogleAdsResponse assetResponse(String resourceName) {
        return MutateGoogleAdsResponse.newBuilder()
                .addMutateOperationResponses(MutateOperationResponse.newBuilder()
                        .setAssetResult(MutateAssetResult.newBuilder().setResourceName(resourceName)))
                .build();
    }
}
