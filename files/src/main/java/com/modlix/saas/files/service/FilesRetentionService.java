package com.modlix.saas.files.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.dto.App;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.dao.FileSystemDao.ExpiredFile;
import com.modlix.saas.files.dao.FilesRetentionPolicyDao;
import com.modlix.saas.files.dto.FilesRetentionPolicy;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyAppliesTo;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyLevel;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyResourceType;
import com.modlix.saas.files.model.retention.RetentionEffective;
import com.modlix.saas.files.model.retention.RetentionGroup;
import com.modlix.saas.files.model.retention.RetentionPreview;
import com.modlix.saas.files.service.RetentionPolicyResolver.Resolved;

/**
 * Path retention policies: "files under {@code <appCode>/<path>} older than N days are removed",
 * registered by the app owner and tuned per client.
 *
 * <p>Who may do what:
 * <ul>
 * <li>Every call made by a user (CRUD, effective, preview) needs {@code Authorities.Files_RETENTION}.</li>
 * <li>APP rows: the caller's client must be the client that owns (created) the app.</li>
 * <li>CLIENT rows: the row's client must be the caller's client or one it manages, and the prefix
 * must already be registered by an APP row. Clients tune prefixes, they never invent them.</li>
 * </ul>
 *
 * <p>The sweep ({@link #applyRetention}) is internal and driven by the worker.
 */
@Service
public class FilesRetentionService {

    public static final String RETENTION_AUTHORITY = "Authorities.Files_RETENTION";

    static final int PREVIEW_SAMPLE_SIZE = 20;

    private static final String CACHE_NAME_POLICIES = "filesRetentionPolicies";
    private static final String CACHE_NAME_HIERARCHY = "filesRetentionHierarchy";

    private static final Logger logger = LoggerFactory.getLogger(FilesRetentionService.class);

    private final FilesRetentionPolicyDao dao;
    private final FileSystemDao fileSystemDao;
    private final IFeignSecurityService securityFeign;
    private final FeignAuthenticationService authService;
    private final CacheService cacheService;
    private final FilesMessageResourceService msgService;

    public FilesRetentionService(FilesRetentionPolicyDao dao, FileSystemDao fileSystemDao,
            IFeignSecurityService securityFeign, FeignAuthenticationService authService, CacheService cacheService,
            FilesMessageResourceService msgService) {

        this.dao = dao;
        this.fileSystemDao = fileSystemDao;
        this.securityFeign = securityFeign;
        this.authService = authService;
        this.cacheService = cacheService;
        this.msgService = msgService;
    }

    // ---------------------------------------------------------------------------------------------
    // CRUD
    // ---------------------------------------------------------------------------------------------

    /**
     * The app's registered prefixes (APP rows) and the CLIENT rows written by {@code clientCode}
     * (the caller's client when absent).
     */
    public List<FilesRetentionPolicy> list(String appCode, String clientCode) {

        // Reading what applies to your own (or a managed) client needs no special permission; changing it does.
        ContextAuthentication ca = this.requireSignedIn();
        this.requireField(appCode, "appCode");

        String target = StringUtil.safeIsBlank(clientCode) ? ca.getClientCode() : clientCode.trim();
        this.checkClientAccess(ca, target);

        return this.dao.readByApp(appCode.trim(), null).stream()
                .filter(p -> p.getLevel() == FilesRetentionPolicyLevel.APP
                        || StringUtil.safeEquals(p.getClientCode(), target))
                .toList();
    }

    public FilesRetentionPolicy create(FilesRetentionPolicy entity) {

        ContextAuthentication ca = this.requireRetentionAuthority();

        if (entity == null)
            return this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.MISSING_FIELD, "body");

        this.requireField(entity.getAppCode(), "appCode");
        if (entity.getResourceType() == null)
            this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.MISSING_FIELD, "resourceType");
        if (entity.getLevel() == null)
            this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.MISSING_FIELD, "level");

        entity.setAppCode(entity.getAppCode().trim());
        entity.setPath(this.normalizePath(entity.getPath()));
        this.validateDays(entity.getRetentionDays());

        if (entity.getLevel() == FilesRetentionPolicyLevel.APP) {

            this.checkAppOwner(ca, entity.getAppCode());
            // The APP row is the app default for every client; it belongs to the owner and has no scope.
            entity.setClientCode(ca.getClientCode());
            entity.setAppliesTo(FilesRetentionPolicyAppliesTo.SELF);
        } else {

            entity.setClientCode(StringUtil.safeIsBlank(entity.getClientCode()) ? ca.getClientCode()
                    : entity.getClientCode().trim());
            if (entity.getAppliesTo() == null)
                entity.setAppliesTo(FilesRetentionPolicyAppliesTo.SELF);

            this.checkClientAccess(ca, entity.getClientCode());

            if (!this.dao.existsAppRow(entity.getAppCode(), entity.getResourceType(), entity.getPath()))
                this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.RETENTION_PREFIX_NOT_REGISTERED,
                        entity.getPath(), entity.getAppCode(), entity.getResourceType().getLiteral());
        }

        if (this.dao.exists(entity))
            this.fail(HttpStatus.CONFLICT, FilesMessageResourceService.ALREADY_EXISTS, "Retention policy",
                    entity.getAppCode() + "/" + entity.getPath());

        FilesRetentionPolicy created = this.dao.create(entity, this.userId(ca));
        this.evictCaches();
        return created;
    }

    /** Changes the days of a row. Path, client, scope and level are its identity and do not change. */
    public FilesRetentionPolicy update(ULong id, FilesRetentionPolicy entity) {

        ContextAuthentication ca = this.requireRetentionAuthority();
        FilesRetentionPolicy existing = this.readExisting(id);
        this.checkRowAccess(ca, existing);

        Integer days = entity == null ? null : entity.getRetentionDays();
        this.validateDays(days);

        this.dao.updateDays(existing.getId(), days, this.userId(ca));
        this.evictCaches();
        return this.dao.readById(existing.getId());
    }

    /** Deletes a row. Deleting an APP row unregisters the prefix and so also removes its client rows. */
    public Boolean delete(ULong id) {

        ContextAuthentication ca = this.requireRetentionAuthority();
        FilesRetentionPolicy existing = this.readExisting(id);
        this.checkRowAccess(ca, existing);

        if (existing.getLevel() == FilesRetentionPolicyLevel.APP)
            this.dao.deleteClientRowsOfPrefix(existing.getAppCode(), existing.getResourceType(),
                    existing.getPath());

        boolean deleted = this.dao.deleteById(existing.getId()) > 0;
        this.evictCaches();
        return deleted;
    }

    // ---------------------------------------------------------------------------------------------
    // Effective policy and dry run
    // ---------------------------------------------------------------------------------------------

    /** The resolved retention per registered prefix for {@code clientCode} (the caller's client when absent). */
    public List<RetentionEffective> effective(String appCode, String clientCode, String resourceType) {

        ContextAuthentication ca = this.requireSignedIn();
        this.requireField(appCode, "appCode");

        String app = appCode.trim();
        String target = StringUtil.safeIsBlank(clientCode) ? ca.getClientCode() : clientCode.trim();
        this.checkClientAccess(ca, target);

        List<FilesRetentionPolicyResourceType> types = StringUtil.safeIsBlank(resourceType)
                ? List.of(FilesRetentionPolicyResourceType.STATIC, FilesRetentionPolicyResourceType.SECURED)
                : List.of(this.parseType(resourceType));

        List<String> hierarchy = this.hierarchy(target);
        List<RetentionEffective> result = new ArrayList<>();

        for (FilesRetentionPolicyResourceType type : types) {

            List<FilesRetentionPolicy> rows = this.policyRows(app, type);

            for (FilesRetentionPolicy appRow : rows) {

                if (appRow.getLevel() != FilesRetentionPolicyLevel.APP)
                    continue;

                String prefix = appRow.getPath();
                Resolved resolved = RetentionPolicyResolver.resolve(prefix, rows, hierarchy);
                FilesRetentionPolicy own = RetentionPolicyResolver.clientRow(prefix, rows, target,
                        FilesRetentionPolicyAppliesTo.SELF);
                FilesRetentionPolicy managed = RetentionPolicyResolver.clientRow(prefix, rows, target,
                        FilesRetentionPolicyAppliesTo.MANAGED);

                result.add(new RetentionEffective(app, target, type.getLiteral(), prefix, resolved.days(),
                        resolved.keepForever(), resolved.source(), appRow.getId(), appRow.getRetentionDays(),
                        own == null ? null : own.getId(), own == null ? null : own.getRetentionDays(),
                        managed == null ? null : managed.getId(), managed == null ? null : managed.getRetentionDays()));
            }
        }

        return result;
    }

    /**
     * What would be removed now under {@code <appCode>/<path>} for {@code clientCode} (the caller's
     * client when absent). With {@code days} the dry run uses that value, so it can be shown before
     * a policy is saved; without it, the client's resolved retention of the prefix covering the path.
     * Folders of longer registered prefixes inside the path are left out, exactly as in the sweep.
     */
    public RetentionPreview preview(String appCode, String resourceType, String path, String clientCode,
            Integer days) {

        ContextAuthentication ca = this.requireRetentionAuthority();
        this.requireField(appCode, "appCode");

        String app = appCode.trim();
        FilesRetentionPolicyResourceType type = this.parseType(resourceType);
        String prefix = this.normalizePath(path);
        String target = StringUtil.safeIsBlank(clientCode) ? ca.getClientCode() : clientCode.trim();
        this.checkClientAccess(ca, target);

        List<FilesRetentionPolicy> rows = this.policyRows(app, type);
        List<String> registered = appPrefixes(rows);

        Integer effectiveDays;
        String source;

        if (days != null) {
            this.validateDays(days);
            effectiveDays = days;
            source = "PREVIEW";
        } else {
            String covering = RetentionPolicyResolver.longestPrefix(registered, prefix);
            Resolved resolved = covering == null ? null
                    : RetentionPolicyResolver.resolve(covering, rows, this.hierarchy(target));
            effectiveDays = resolved == null ? null : resolved.days();
            source = resolved == null ? null : resolved.source();
        }

        if (effectiveDays == null)
            return new RetentionPreview(app, target, type.getLiteral(), prefix, null, source, 0L, 0L, List.of());

        FilesFileSystemType fsType = toFileSystemType(type);
        ULong root = this.fileSystemDao.readRootFolderId(fsType, target, app);
        ULong folder = root == null ? null
                : this.fileSystemDao.readSubFolderId(fsType, target, root, RetentionPolicyResolver.segments(prefix));

        if (folder == null)
            return new RetentionPreview(app, target, type.getLiteral(), prefix, effectiveDays, source, 0L, 0L,
                    List.of());

        List<ULong> excluded = this.nestedFolderIds(fsType, target, root, registered, prefix);
        String folderPath = folderPath(app, prefix);

        FileSystemDao.RetentionPreviewCount count = this.fileSystemDao.countRetentionExpired(fsType, target, folder,
                excluded, effectiveDays);
        List<String> samples = this.fileSystemDao.readRetentionExpired(fsType, target, folder, folderPath, excluded,
                effectiveDays, PREVIEW_SAMPLE_SIZE).stream().map(ExpiredFile::path).toList();

        return new RetentionPreview(app, target, type.getLiteral(), prefix, effectiveDays, source, count.count(),
                count.totalBytes(), samples);
    }

    // ---------------------------------------------------------------------------------------------
    // Sweep
    // ---------------------------------------------------------------------------------------------

    /**
     * Removes files of one file system type that a retention policy says are old enough, at most
     * {@code limit} per call, and returns what was removed grouped by client and app.
     *
     * <p>Only files under a registered prefix are ever considered. For each registered prefix of
     * each app, every client with an {@code <appCode>} root folder gets its own resolved days; keep
     * forever skips it. Files with their own lifetime (EXPIRES_AFTER_MINUTES) are left to the
     * lifetime cleanup, and files under a longer registered prefix are left to that prefix.
     *
     * <p>A client whose hierarchy cannot be read is skipped rather than resolved without its
     * managers: a manager's keep-forever must never be lost to a failed call.
     *
     * @param deleter removes the given files (object then row) and returns those actually removed
     */
    public List<RetentionGroup> applyRetention(FilesFileSystemType type, int limit,
            Function<List<ExpiredFile>, List<ExpiredFile>> deleter) {

        Map<String, RetentionGroup> groups = new LinkedHashMap<>();
        if (limit <= 0)
            return new ArrayList<>();

        FilesRetentionPolicyResourceType policyType = FilesRetentionPolicyResourceType.lookupLiteral(type.getLiteral());

        Map<String, List<String>> prefixesByApp = new LinkedHashMap<>();
        for (FilesRetentionPolicy row : this.dao.readAppRows(policyType))
            prefixesByApp.computeIfAbsent(row.getAppCode(), k -> new ArrayList<>()).add(row.getPath());

        int remaining = limit;

        appLoop:
        for (Map.Entry<String, List<String>> appEntry : prefixesByApp.entrySet()) {

            String appCode = appEntry.getKey();
            List<String> prefixes = appEntry.getValue();
            List<FilesRetentionPolicy> rows = this.dao.readByApp(appCode, policyType);
            Map<String, ULong> roots = this.fileSystemDao.readAppRootFolders(type, appCode);

            for (Map.Entry<String, ULong> rootEntry : roots.entrySet()) {

                String clientCode = rootEntry.getKey();
                List<String> hierarchy;

                try {
                    hierarchy = this.hierarchy(clientCode);
                } catch (Exception e) {
                    logger.error("Skipping retention of {} for {}: client hierarchy unavailable", appCode,
                            clientCode, e);
                    continue;
                }

                Map<String, ULong> folders = new LinkedHashMap<>();

                for (String prefix : prefixes) {

                    try {
                        Resolved resolved = RetentionPolicyResolver.resolve(prefix, rows, hierarchy);
                        if (resolved == null || resolved.keepForever())
                            continue;

                        ULong folder = this.folderOf(folders, type, clientCode, rootEntry.getValue(), prefix);
                        if (folder == null)
                            continue;

                        List<ULong> excluded = new ArrayList<>();
                        for (String nested : RetentionPolicyResolver.nestedPrefixes(prefixes, prefix)) {
                            ULong id = this.folderOf(folders, type, clientCode, rootEntry.getValue(), nested);
                            if (id != null)
                                excluded.add(id);
                        }

                        List<ExpiredFile> files = this.fileSystemDao.readRetentionExpired(type, clientCode, folder,
                                folderPath(appCode, prefix), excluded, resolved.days(), remaining);

                        if (files.isEmpty())
                            continue;

                        remaining -= files.size();

                        List<ExpiredFile> removed = deleter.apply(files);
                        if (removed != null && !removed.isEmpty())
                            groups.computeIfAbsent(clientCode + "\u0000" + appCode,
                                    k -> new RetentionGroup(clientCode, appCode, type.getLiteral()))
                                    .paths().addAll(removed.stream().map(ExpiredFile::path).toList());

                        if (remaining <= 0)
                            break appLoop;
                    } catch (Exception e) {
                        logger.error("Retention of {}/{} for {} failed", appCode, prefix, clientCode, e);
                    }
                }
            }
        }

        int total = groups.values().stream().mapToInt(g -> g.paths().size()).sum();
        if (total > 0)
            logger.info("Retention removed {} file(s) from {}", total, type);

        return new ArrayList<>(groups.values());
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private ULong folderOf(Map<String, ULong> cache, FilesFileSystemType type, String clientCode, ULong root,
            String prefix) {

        if (cache.containsKey(prefix))
            return cache.get(prefix);

        ULong id = this.fileSystemDao.readSubFolderId(type, clientCode, root, RetentionPolicyResolver.segments(prefix));
        cache.put(prefix, id);
        return id;
    }

    private List<ULong> nestedFolderIds(FilesFileSystemType type, String clientCode, ULong root,
            List<String> registered, String prefix) {

        return RetentionPolicyResolver.nestedPrefixes(registered, prefix).stream()
                .map(nested -> this.fileSystemDao.readSubFolderId(type, clientCode, root,
                        RetentionPolicyResolver.segments(nested)))
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<String> appPrefixes(List<FilesRetentionPolicy> rows) {
        return rows.stream().filter(r -> r.getLevel() == FilesRetentionPolicyLevel.APP)
                .map(FilesRetentionPolicy::getPath).toList();
    }

    private static String folderPath(String appCode, String prefix) {
        return "/" + appCode + (StringUtil.safeIsBlank(prefix) ? "" : "/" + prefix);
    }

    static FilesFileSystemType toFileSystemType(FilesRetentionPolicyResourceType type) {
        return FilesFileSystemType.lookupLiteral(type.getLiteral());
    }

    /** Rows of an app and type, cached until any policy write. */
    private List<FilesRetentionPolicy> policyRows(String appCode, FilesRetentionPolicyResourceType type) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_POLICIES,
                () -> new ArrayList<>(this.dao.readByApp(appCode, type)), appCode, ":", type.getLiteral());
    }

    /**
     * The client followed by its managers, nearest first, from security. Cached per client; the
     * cache is also cleared on any policy write.
     */
    List<String> hierarchy(String clientCode) {

        List<String> list = this.cacheService.cacheValueOrGet(CACHE_NAME_HIERARCHY, () -> {
            List<String> codes = this.securityFeign.getClientHierarchyCodes(clientCode);
            if (codes == null || codes.isEmpty())
                return null;
            List<String> chain = new ArrayList<>(codes);
            if (!StringUtil.safeEquals(chain.getFirst(), clientCode))
                chain.addFirst(clientCode);
            return chain;
        }, clientCode);

        if (list == null)
            throw new GenericException(HttpStatus.NOT_FOUND, "Unknown client " + clientCode);

        return list;
    }

    private void evictCaches() {
        this.cacheService.evictAll(CACHE_NAME_POLICIES);
        this.cacheService.evictAll(CACHE_NAME_HIERARCHY);
    }

    /** Reads: any signed-in user; which client they may read is checked separately (checkClientAccess). */
    private ContextAuthentication requireSignedIn() {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (ca == null || ca.getUser() == null || !ca.isAuthenticated())
            return this.fail(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PERMISSION,
                    "a signed-in user");

        return ca;
    }

    private ContextAuthentication requireRetentionAuthority() {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (ca == null || ca.getUser() == null
                || !SecurityContextUtil.hasAuthority(RETENTION_AUTHORITY, ca.getAuthorities()))
            return this.fail(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PERMISSION,
                    RETENTION_AUTHORITY);

        return ca;
    }

    /** APP rows: the caller's client must be the client that owns the app. */
    private void checkAppOwner(ContextAuthentication ca, String appCode) {

        App app;
        try {
            app = this.securityFeign.getAppByCode(appCode);
        } catch (Exception e) {
            logger.debug("App lookup failed for {}", appCode, e);
            app = null;
        }

        if (app == null) {
            this.fail(HttpStatus.NOT_FOUND, FilesMessageResourceService.OBJECT_NOT_FOUND, "App", appCode);
            return;
        }

        BigInteger userClientId = ca.getUser().getClientId();
        if (app.getClientId() == null || userClientId == null || !app.getClientId().equals(userClientId))
            this.fail(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PERMISSION,
                    "owner of the app " + appCode);
    }

    /** CLIENT rows and reads: the client must be the caller's client or one it manages. */
    private void checkClientAccess(ContextAuthentication ca, String clientCode) {

        if (StringUtil.safeEquals(clientCode, ca.getClientCode()))
            return;

        boolean managed;
        try {
            BigInteger clientId = this.authService.getClientIdByCode(clientCode);
            managed = clientId != null && Boolean.TRUE.equals(this.authService.isUserClientManageClient(
                    ca.getUrlAppCode(), ca.getUser().getId(), ca.getUser().getClientId(), clientId));
        } catch (Exception e) {
            logger.debug("Managed client check failed for {}", clientCode, e);
            managed = false;
        }

        if (!managed)
            this.fail(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PERMISSION,
                    "manager of the client " + clientCode);
    }

    private void checkRowAccess(ContextAuthentication ca, FilesRetentionPolicy row) {

        if (row.getLevel() == FilesRetentionPolicyLevel.APP)
            this.checkAppOwner(ca, row.getAppCode());
        else
            this.checkClientAccess(ca, row.getClientCode());
    }

    private FilesRetentionPolicy readExisting(ULong id) {

        FilesRetentionPolicy existing = id == null ? null : this.dao.readById(id);
        if (existing == null)
            return this.fail(HttpStatus.NOT_FOUND, FilesMessageResourceService.OBJECT_NOT_FOUND, "Retention policy",
                    id);
        return existing;
    }

    private FilesRetentionPolicyResourceType parseType(String resourceType) {

        FilesRetentionPolicyResourceType type = StringUtil.safeIsBlank(resourceType) ? null
                : FilesRetentionPolicyResourceType.lookupLiteral(resourceType.trim().toUpperCase());

        if (type == null)
            return this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.INVALID_FIELD, "resourceType",
                    resourceType);
        return type;
    }

    private String normalizePath(String path) {
        try {
            return RetentionPolicyResolver.normalizePath(path);
        } catch (IllegalArgumentException e) {
            return this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.INVALID_FIELD, "path", path);
        }
    }

    private void validateDays(Integer days) {
        if (days != null && days < 1)
            this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.INVALID_FIELD, "retentionDays", days);
    }

    private void requireField(String value, String name) {
        if (StringUtil.safeIsBlank(value))
            this.fail(HttpStatus.BAD_REQUEST, FilesMessageResourceService.MISSING_FIELD, name);
    }

    private ULong userId(ContextAuthentication ca) {
        BigInteger id = ca.getUser().getId();
        return id == null ? null : ULong.valueOf(id);
    }

    private <T> T fail(HttpStatus status, String messageId, Object... params) {
        return this.msgService.throwMessage(msg -> new GenericException(status, msg), messageId, params);
    }
}
