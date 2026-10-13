package com.modlix.saas.files.model.retention;

import java.io.Serializable;

import org.jooq.types.ULong;

/**
 * The retention that applies to one client for one registered prefix, and where it comes from.
 *
 * <p>{@code days} null is keep forever. {@code source} is {@code APP}, {@code OWN} or
 * {@code MANAGER:<clientCode>}. The {@code own*} and {@code managed*} fields describe the client's
 * own SELF and MANAGED rows for this prefix (ids null when there is no such row), so a settings
 * page can offer "override" and "reset to inherited".
 */
public record RetentionEffective(
        String appCode,
        String clientCode,
        String resourceType,
        String path,
        Integer days,
        boolean keepForever,
        String source,
        ULong appPolicyId,
        Integer appDays,
        ULong ownPolicyId,
        Integer ownDays,
        ULong managedPolicyId,
        Integer managedDays) implements Serializable {
}
