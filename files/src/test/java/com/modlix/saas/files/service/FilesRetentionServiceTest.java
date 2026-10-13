package com.modlix.saas.files.service;

import static com.modlix.saas.files.service.RetentionPolicyResolverTest.appRow;
import static com.modlix.saas.files.service.RetentionPolicyResolverTest.clientRow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.dto.App;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.dao.FileSystemDao.ExpiredFile;
import com.modlix.saas.files.dao.FilesRetentionPolicyDao;
import com.modlix.saas.files.dto.FilesRetentionPolicy;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyAppliesTo;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyLevel;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyResourceType;
import com.modlix.saas.files.model.retention.RetentionGroup;

/** Who may write retention policies, and what the sweep picks. */
class FilesRetentionServiceTest {

    private static final BigInteger OWNER_ID = BigInteger.valueOf(10);
    private static final BigInteger OTHER_ID = BigInteger.valueOf(20);
    private static final String PREFIX = "whatsapp/incoming";

    private FilesRetentionPolicyDao dao;
    private FileSystemDao fsDao;
    private IFeignSecurityService securityFeign;
    private FeignAuthenticationService authService;
    private FilesRetentionService service;

    /** Caches nothing: every read goes to the supplier. */
    private static final class PassThroughCache extends CacheService {
        @Override
        public <T> T cacheValueOrGet(String cName, Supplier<T> supplier, Object... keys) {
            return supplier.get();
        }

        @Override
        public boolean evictAll(String cName) {
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        dao = mock(FilesRetentionPolicyDao.class);
        fsDao = mock(FileSystemDao.class);
        securityFeign = mock(IFeignSecurityService.class);
        authService = mock(FeignAuthenticationService.class);
        service = new FilesRetentionService(dao, fsDao, securityFeign, authService, new PassThroughCache(),
                new FilesMessageResourceService());

        lenient().when(securityFeign.getAppByCode("app")).thenReturn(new App().setAppCode("app").setClientId(OWNER_ID));
        lenient().when(dao.create(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String clientCode, BigInteger clientId, String... authorities) {
        ContextUser user = new ContextUser();
        user.setId(BigInteger.ONE);
        user.setClientId(clientId);
        user.setStringAuthorities(List.of(authorities));

        ContextAuthentication ca = new ContextAuthentication();
        ca.setUser(user);
        ca.setAuthenticated(true);
        ca.setClientCode(clientCode);
        ca.setUrlAppCode("app");
        SecurityContextHolder.getContext().setAuthentication(ca);
    }

    private static FilesRetentionPolicy newRow(FilesRetentionPolicyLevel level, String clientCode, Integer days) {
        return new FilesRetentionPolicy().setAppCode("app").setClientCode(clientCode)
                .setResourceType(FilesRetentionPolicyResourceType.SECURED).setPath("/" + PREFIX + "/")
                .setRetentionDays(days).setLevel(level);
    }

    private static HttpStatus statusOf(Runnable call) {
        return assertThrows(GenericException.class, call::run).getStatusCode();
    }

    // ---------------------------------------------------------------- authorization

    @Test
    void missingRetentionAuthorityIsRefused() {
        signIn("OWNER", OWNER_ID, "Authorities.Logged_IN", "Authorities.STATIC_Files_PATH");

        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.create(newRow(FilesRetentionPolicyLevel.APP, null, 30))));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.preview("app", "SECURED", PREFIX, null, 30)));
        verify(dao, never()).create(any(), any());
    }

    @Test
    void readingWhatAppliesNeedsOnlyASignedInUserWithAccessToTheClient() {
        signIn("OWNER", OWNER_ID, "Authorities.Logged_IN");
        when(securityFeign.getClientHierarchyCodes("OWNER")).thenReturn(List.of("OWNER", "SYSTEM"));

        // Own client: allowed without Files_RETENTION.
        assertDoesNotThrow(() -> service.effective("app", null, null));
        assertDoesNotThrow(() -> service.list("app", null));
        // A client the caller neither is nor manages: still refused.
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.effective("app", "STRANGER", null)));
    }

    @Test
    void appRowByTheOwnerIsCreatedNormalised() {
        signIn("OWNER", OWNER_ID, FilesRetentionService.RETENTION_AUTHORITY);

        FilesRetentionPolicy created = service.create(newRow(FilesRetentionPolicyLevel.APP, "SOMEONE", 30));

        assertEquals("OWNER", created.getClientCode());
        assertEquals(PREFIX, created.getPath());
        assertEquals(FilesRetentionPolicyAppliesTo.SELF, created.getAppliesTo());
    }

    @Test
    void appRowByANonOwnerIsRefused() {
        signIn("OTHER", OTHER_ID, FilesRetentionService.RETENTION_AUTHORITY);

        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> service.create(newRow(FilesRetentionPolicyLevel.APP, null, 30))));
        verify(dao, never()).create(any(), any());
    }

    @Test
    void clientRowWithoutAnAppRowIsRefused() {
        signIn("OTHER", OTHER_ID, FilesRetentionService.RETENTION_AUTHORITY);
        when(dao.existsAppRow("app", FilesRetentionPolicyResourceType.SECURED, PREFIX)).thenReturn(false);

        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> service.create(newRow(FilesRetentionPolicyLevel.CLIENT, null, 10))));
        verify(dao, never()).create(any(), any());
    }

    @Test
    void clientRowOnARegisteredPrefixIsCreatedForTheCaller() {
        signIn("OTHER", OTHER_ID, FilesRetentionService.RETENTION_AUTHORITY);
        when(dao.existsAppRow("app", FilesRetentionPolicyResourceType.SECURED, PREFIX)).thenReturn(true);

        FilesRetentionPolicy created = service.create(newRow(FilesRetentionPolicyLevel.CLIENT, null, 10));

        assertEquals("OTHER", created.getClientCode());
        assertEquals(FilesRetentionPolicyAppliesTo.SELF, created.getAppliesTo());
    }

    @Test
    void clientRowForAClientTheCallerDoesNotManageIsRefused() {
        signIn("OTHER", OTHER_ID, FilesRetentionService.RETENTION_AUTHORITY);
        when(authService.getClientIdByCode("STRANGER")).thenReturn(BigInteger.valueOf(99));
        when(authService.isUserClientManageClient(anyString(), any(), any(), any())).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> service.create(newRow(FilesRetentionPolicyLevel.CLIENT, "STRANGER", 10))));
    }

    @Test
    void clientRowForAManagedClientIsAllowed() {
        signIn("OTHER", OTHER_ID, FilesRetentionService.RETENTION_AUTHORITY);
        when(authService.getClientIdByCode("CHILD")).thenReturn(BigInteger.valueOf(99));
        when(authService.isUserClientManageClient("app", BigInteger.ONE, OTHER_ID, BigInteger.valueOf(99)))
                .thenReturn(true);
        when(dao.existsAppRow("app", FilesRetentionPolicyResourceType.SECURED, PREFIX)).thenReturn(true);

        FilesRetentionPolicy row = newRow(FilesRetentionPolicyLevel.CLIENT, "CHILD", 10)
                .setAppliesTo(FilesRetentionPolicyAppliesTo.MANAGED);

        assertEquals("CHILD", service.create(row).getClientCode());
    }

    @Test
    void zeroDaysAndDuplicatesAreRefused() {
        signIn("OWNER", OWNER_ID, FilesRetentionService.RETENTION_AUTHORITY);

        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> service.create(newRow(FilesRetentionPolicyLevel.APP, null, 0))));

        when(dao.exists(any())).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> service.create(newRow(FilesRetentionPolicyLevel.APP, null, 30))));
    }

    @Test
    void deletingAnAppRowRemovesItsClientRows() {
        signIn("OWNER", OWNER_ID, FilesRetentionService.RETENTION_AUTHORITY);
        FilesRetentionPolicy existing = appRow(PREFIX, 30);
        existing.setId(ULong.valueOf(5));
        when(dao.readById(ULong.valueOf(5))).thenReturn(existing);
        when(dao.deleteById(ULong.valueOf(5))).thenReturn(1);

        assertTrue(service.delete(ULong.valueOf(5)));
        verify(dao).deleteClientRowsOfPrefix("app", FilesRetentionPolicyResourceType.SECURED, PREFIX);
    }

    @Test
    void updatingAnAppRowByANonOwnerIsRefused() {
        signIn("OTHER", OTHER_ID, FilesRetentionService.RETENTION_AUTHORITY);
        FilesRetentionPolicy existing = appRow(PREFIX, 30);
        existing.setId(ULong.valueOf(5));
        when(dao.readById(ULong.valueOf(5))).thenReturn(existing);

        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> service.update(ULong.valueOf(5), new FilesRetentionPolicy().setRetentionDays(1))));
        verify(dao, never()).updateDays(any(), any(), any());
    }

    // ---------------------------------------------------------------- sweep

    private static ExpiredFile file(long id, String client, String path) {
        return new ExpiredFile(ULong.valueOf(id), client, FilesFileSystemType.SECURED, path);
    }

    @Test
    void sweepUsesEachClientsResolvedDaysSkipsKeepForeverAndExcludesNestedPrefixes() {

        // "" (whole app) 30 days, "whatsapp/incoming" 7 days, "whatsapp/avatars" keep forever.
        List<FilesRetentionPolicy> rows = List.of(appRow("", 30), appRow(PREFIX, 7), appRow("whatsapp/avatars", null),
                clientRow(PREFIX, "KEEP", FilesRetentionPolicyAppliesTo.SELF, null));
        when(dao.readAppRows(FilesRetentionPolicyResourceType.SECURED))
                .thenReturn(rows.stream().filter(r -> r.getLevel() == FilesRetentionPolicyLevel.APP).toList());
        when(dao.readByApp("app", FilesRetentionPolicyResourceType.SECURED)).thenReturn(rows);
        when(fsDao.readAppRootFolders(FilesFileSystemType.SECURED, "app"))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of("C1", ULong.valueOf(100))));
        when(securityFeign.getClientHierarchyCodes("C1")).thenReturn(List.of("C1", "SYSTEM"));

        // Folder ids: "" -> root 100, incoming -> 101, avatars -> 102.
        when(fsDao.readSubFolderId(eq(FilesFileSystemType.SECURED), eq("C1"), eq(ULong.valueOf(100)), anyList()))
                .thenAnswer(inv -> {
                    List<String> segs = inv.getArgument(3);
                    String p = String.join("/", segs);
                    return switch (p) {
                        case "" -> ULong.valueOf(100);
                        case PREFIX -> ULong.valueOf(101);
                        case "whatsapp/avatars" -> ULong.valueOf(102);
                        default -> null;
                    };
                });

        when(fsDao.readRetentionExpired(eq(FilesFileSystemType.SECURED), eq("C1"), eq(ULong.valueOf(100)),
                eq("/app"), any(), eq(30), anyInt()))
                .thenReturn(List.of(file(1, "C1", "/app/docs/a.pdf")));
        when(fsDao.readRetentionExpired(eq(FilesFileSystemType.SECURED), eq("C1"), eq(ULong.valueOf(101)),
                eq("/app/" + PREFIX), any(), eq(7), anyInt()))
                .thenReturn(List.of(file(2, "C1", "/app/" + PREFIX + "/b.jpg")));

        List<List<ExpiredFile>> deleted = new ArrayList<>();
        List<RetentionGroup> groups = service.applyRetention(FilesFileSystemType.SECURED, 500, files -> {
            deleted.add(files);
            return files;
        });

        assertEquals(1, groups.size());
        assertEquals("C1", groups.getFirst().clientCode());
        assertEquals("app", groups.getFirst().appCode());
        assertEquals("SECURED", groups.getFirst().resourceType());
        assertEquals(List.of("/app/docs/a.pdf", "/app/" + PREFIX + "/b.jpg"), groups.getFirst().paths());

        // The whole-app sweep must not descend into the two nested registered prefixes.
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<ULong>> excluded = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(fsDao).readRetentionExpired(eq(FilesFileSystemType.SECURED), eq("C1"), eq(ULong.valueOf(100)),
                eq("/app"), excluded.capture(), eq(30), eq(500));
        assertEquals(List.of(ULong.valueOf(101), ULong.valueOf(102)), new ArrayList<>(excluded.getValue()));

        // Avatars are keep forever: never read.
        verify(fsDao, never()).readRetentionExpired(any(), any(), eq(ULong.valueOf(102)), any(), any(), anyInt(),
                anyInt());
        assertEquals(2, deleted.size());
    }

    @Test
    void sweepSkipsAClientThatKeepsForeverAndAClientWithoutHierarchy() {

        List<FilesRetentionPolicy> rows = List.of(appRow(PREFIX, 7),
                clientRow(PREFIX, "KEEP", FilesRetentionPolicyAppliesTo.SELF, null));
        when(dao.readAppRows(FilesRetentionPolicyResourceType.SECURED)).thenReturn(List.of(rows.getFirst()));
        when(dao.readByApp("app", FilesRetentionPolicyResourceType.SECURED)).thenReturn(rows);
        when(fsDao.readAppRootFolders(FilesFileSystemType.SECURED, "app"))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of("KEEP", ULong.valueOf(1))));
        when(securityFeign.getClientHierarchyCodes("KEEP")).thenReturn(List.of("KEEP", "SYSTEM"));

        assertTrue(service.applyRetention(FilesFileSystemType.SECURED, 500, f -> f).isEmpty());

        when(fsDao.readAppRootFolders(FilesFileSystemType.SECURED, "app"))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of("GONE", ULong.valueOf(2))));
        when(securityFeign.getClientHierarchyCodes("GONE")).thenReturn(List.of());

        assertTrue(service.applyRetention(FilesFileSystemType.SECURED, 500, f -> f).isEmpty());

        verify(fsDao, never()).readRetentionExpired(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void sweepStopsAtTheLimitAndOnlyReportsWhatWasRemoved() {

        FilesRetentionPolicy app = appRow(PREFIX, 7);
        when(dao.readAppRows(FilesRetentionPolicyResourceType.SECURED)).thenReturn(List.of(app));
        when(dao.readByApp("app", FilesRetentionPolicyResourceType.SECURED)).thenReturn(List.of(app));
        java.util.LinkedHashMap<String, ULong> roots = new java.util.LinkedHashMap<>();
        roots.put("C1", ULong.valueOf(1));
        roots.put("C2", ULong.valueOf(2));
        when(fsDao.readAppRootFolders(FilesFileSystemType.SECURED, "app")).thenReturn(roots);
        when(securityFeign.getClientHierarchyCodes(anyString()))
                .thenAnswer(inv -> List.of((String) inv.getArgument(0)));
        when(fsDao.readSubFolderId(any(), anyString(), any(), anyList())).thenReturn(ULong.valueOf(50));
        when(fsDao.readRetentionExpired(any(), eq("C1"), any(), any(), any(), eq(7), eq(2)))
                .thenReturn(List.of(file(1, "C1", "/app/" + PREFIX + "/1"), file(2, "C1", "/app/" + PREFIX + "/2")));

        // The first file fails to delete; only the second is reported.
        List<RetentionGroup> groups = service.applyRetention(FilesFileSystemType.SECURED, 2,
                files -> List.of(files.get(1)));

        assertEquals(1, groups.size());
        assertEquals(List.of("/app/" + PREFIX + "/2"), groups.getFirst().paths());
        verify(fsDao, never()).readRetentionExpired(any(), eq("C2"), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void previewWithoutAFolderFindsNothingAndKeepForeverReadsNothing() {
        signIn("OTHER", OTHER_ID, FilesRetentionService.RETENTION_AUTHORITY);
        when(dao.readByApp("app", FilesRetentionPolicyResourceType.SECURED))
                .thenReturn(List.of(appRow(PREFIX, null)));
        when(securityFeign.getClientHierarchyCodes("OTHER")).thenReturn(List.of("OTHER", "SYSTEM"));

        var keep = service.preview("app", "secured", PREFIX + "/sub", null, null);
        assertEquals(0L, keep.count());
        assertSame(null, keep.days());
        verify(fsDao, never()).countRetentionExpired(any(), any(), any(), any(), anyInt());

        when(fsDao.readRootFolderId(FilesFileSystemType.SECURED, "OTHER", "app")).thenReturn(null);
        var none = service.preview("app", "SECURED", PREFIX, null, 5);
        assertEquals(5, none.days());
        assertEquals(0L, none.count());
    }
}
