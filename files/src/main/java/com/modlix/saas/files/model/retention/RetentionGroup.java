package com.modlix.saas.files.model.retention;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * What one retention sweep removed for one client of one app. {@code paths} are full paths within
 * the client, starting with {@code /<appCode>/}.
 */
public record RetentionGroup(String clientCode, String appCode, String resourceType, List<String> paths)
        implements Serializable {

    public RetentionGroup(String clientCode, String appCode, String resourceType) {
        this(clientCode, appCode, resourceType, new ArrayList<>());
    }
}
