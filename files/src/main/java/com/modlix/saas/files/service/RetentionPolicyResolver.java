package com.modlix.saas.files.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.files.dto.FilesRetentionPolicy;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyAppliesTo;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyLevel;

/**
 * The pure part of retention: matching a path to a registered prefix and choosing which row of
 * that prefix applies to a client. No I/O, so every rule here is unit tested directly.
 *
 * <p>For a file of client C at {@code <appCode>/<rest>}:
 * <ol>
 * <li>the registered prefix is the longest APP row PATH that is a prefix of {@code rest} on segment
 * boundaries ({@code ""} matches everything);</li>
 * <li>for that prefix C's own SELF row wins, then the first MANAGED row walking C's managers
 * upward (nearest first), then the APP row;</li>
 * <li>a null RETENTION_DAYS on the winning row means keep forever.</li>
 * </ol>
 */
public final class RetentionPolicyResolver {

    public static final String SOURCE_APP = "APP";
    public static final String SOURCE_OWN = "OWN";
    public static final String SOURCE_MANAGER_PREFIX = "MANAGER:";

    private RetentionPolicyResolver() {
    }

    /** The result of resolving one prefix for one client. A null {@code days} is keep forever. */
    public record Resolved(String path, Integer days, String source, Integer appDays) {

        public boolean keepForever() {
            return this.days == null;
        }
    }

    /**
     * Normalises a prefix as stored: trimmed, no leading, trailing or repeated slashes. Blank is
     * {@code ""}, the whole app folder.
     *
     * @throws IllegalArgumentException on a "." or ".." segment or a backslash
     */
    public static String normalizePath(String path) {

        if (StringUtil.safeIsBlank(path))
            return "";

        List<String> parts = new ArrayList<>();
        for (String part : path.trim().split("/")) {
            String p = part.trim();
            if (p.isEmpty())
                continue;
            if (".".equals(p) || "..".equals(p) || p.contains("\\"))
                throw new IllegalArgumentException("Invalid path segment '" + p + "'");
            parts.add(p);
        }

        return String.join("/", parts);
    }

    /** The segments of a normalised path; none for {@code ""}. */
    public static List<String> segments(String path) {
        return StringUtil.safeIsBlank(path) ? List.of() : List.of(path.split("/"));
    }

    /** Whether {@code path} is {@code prefix} or below it, on segment boundaries. */
    public static boolean isUnder(String prefix, String path) {

        if (StringUtil.safeIsBlank(prefix))
            return true;
        if (path == null)
            return false;

        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /**
     * The longest registered prefix covering {@code rest} (the path after {@code <appCode>/}), or
     * null when none does. "whatsapp/in" does not cover "whatsapp/incoming/a.png".
     */
    public static String longestPrefix(Collection<String> registered, String rest) {

        String normalized = normalizePath(rest);
        String best = null;

        for (String prefix : registered) {
            if (prefix == null || !isUnder(prefix, normalized))
                continue;
            if (best == null || prefix.length() > best.length())
                best = prefix;
        }

        return best;
    }

    /**
     * The registered prefixes strictly inside {@code prefix}. Their files belong to them (the longest
     * prefix wins), so a sweep of {@code prefix} must not descend into their folders.
     */
    public static List<String> nestedPrefixes(Collection<String> registered, String prefix) {

        List<String> nested = new ArrayList<>();
        for (String other : registered) {
            if (other == null || other.equals(prefix))
                continue;
            if (isUnder(prefix, other))
                nested.add(other);
        }
        return nested;
    }

    /**
     * Which row of {@code prefix} applies to the client at the head of {@code hierarchy}.
     *
     * @param rows      the rows of the app and resource type (any prefix; others are ignored)
     * @param hierarchy the client followed by its managers, nearest first
     * @return the decision, or null when {@code prefix} has no APP row (it is not registered)
     */
    public static Resolved resolve(String prefix, Collection<FilesRetentionPolicy> rows, List<String> hierarchy) {

        FilesRetentionPolicy appRow = null;
        List<FilesRetentionPolicy> clientRows = new ArrayList<>();

        for (FilesRetentionPolicy row : rows) {
            if (!prefix.equals(row.getPath() == null ? "" : row.getPath()))
                continue;
            if (row.getLevel() == FilesRetentionPolicyLevel.APP)
                appRow = row;
            else
                clientRows.add(row);
        }

        if (appRow == null)
            return null;

        Integer appDays = appRow.getRetentionDays();

        if (hierarchy == null || hierarchy.isEmpty())
            return new Resolved(prefix, appDays, SOURCE_APP, appDays);

        String client = hierarchy.getFirst();

        FilesRetentionPolicy own = find(clientRows, client, FilesRetentionPolicyAppliesTo.SELF);
        if (own != null)
            return new Resolved(prefix, own.getRetentionDays(), SOURCE_OWN, appDays);

        for (int i = 1; i < hierarchy.size(); i++) {
            String manager = hierarchy.get(i);
            if (StringUtil.safeEquals(manager, client))
                continue;
            FilesRetentionPolicy managed = find(clientRows, manager, FilesRetentionPolicyAppliesTo.MANAGED);
            if (managed != null)
                return new Resolved(prefix, managed.getRetentionDays(), SOURCE_MANAGER_PREFIX + manager, appDays);
        }

        return new Resolved(prefix, appDays, SOURCE_APP, appDays);
    }

    /** The CLIENT row of {@code prefix} written by {@code clientCode} with {@code appliesTo}, or null. */
    public static FilesRetentionPolicy clientRow(String prefix, Collection<FilesRetentionPolicy> rows,
            String clientCode, FilesRetentionPolicyAppliesTo appliesTo) {

        List<FilesRetentionPolicy> clientRows = new ArrayList<>();
        for (FilesRetentionPolicy row : rows)
            if (row.getLevel() == FilesRetentionPolicyLevel.CLIENT
                    && prefix.equals(row.getPath() == null ? "" : row.getPath()))
                clientRows.add(row);

        return find(clientRows, clientCode, appliesTo);
    }

    private static FilesRetentionPolicy find(Collection<FilesRetentionPolicy> rows, String clientCode,
            FilesRetentionPolicyAppliesTo appliesTo) {

        for (FilesRetentionPolicy row : rows) {
            FilesRetentionPolicyAppliesTo rowAppliesTo = row.getAppliesTo() == null ? FilesRetentionPolicyAppliesTo.SELF
                    : row.getAppliesTo();
            if (rowAppliesTo == appliesTo && StringUtil.safeEquals(row.getClientCode(), clientCode))
                return row;
        }
        return null;
    }
}
