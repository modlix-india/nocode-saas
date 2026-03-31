package com.fincity.saas.inttest.base;

import io.restassured.response.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves LeadZump profile IDs by name via the Security API.
 *
 * Usage:
 *   ProfileHelper profiles = ProfileHelper.load(secApi, token, clientCode, appCode);
 *   Number salesMemberId = profiles.getByName("Sales Member");
 */
public class ProfileHelper {

    private final Map<String, Number> byName = new HashMap<>();

    private ProfileHelper() {}

    /**
     * Load profiles from the Security API.
     */
    public static ProfileHelper load(SecurityApi secApi, String token, String clientCode,
            String appCode) {
        ProfileHelper helper = new ProfileHelper();

        Response appRes = secApi.getAppByCode(token, clientCode, appCode);
        Number appId = (appRes.statusCode() == 200) ? appRes.body().path("id") : null;

        if (appId == null) {
            appRes = secApi.getAppByCode(token, "SYSTEM", appCode);
            appId = (appRes.statusCode() == 200) ? appRes.body().path("id") : null;
        }

        assertThat(appId).as("App ID for appCode '" + appCode + "' should be resolved from API").isNotNull();

        Response profRes = secApi.listProfiles(token, clientCode, appCode, appId);
        if (profRes.statusCode() != 200) {
            profRes = secApi.listProfiles(token, "SYSTEM", appCode, appId);
        }
        assertThat(profRes.statusCode()).as("Profile list API should return 200").isEqualTo(200);

        List<Map<String, Object>> profiles = profRes.body().path("content");
        assertThat(profiles).as("Profile list should not be empty").isNotNull().isNotEmpty();

        for (Map<String, Object> p : profiles) {
            String name = (String) p.get("name");
            Number id = (Number) p.get("id");
            if (name != null && id != null) {
                helper.byName.put(name, id);
            }
        }

        return helper;
    }

    public Number getByName(String name) {
        Number id = byName.get(name);
        assertThat(id).as("Profile '" + name + "' should exist. Available: " + byName.keySet()).isNotNull();
        return id;
    }

    public Number findByName(String name) {
        return byName.get(name);
    }
}
