package com.fincity.saas.ui.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trust boundary for analytics queries forwarded to PostHog.
 *
 * Forces every outbound query to carry tenant filters
 * (app_code = X AND client_code = Y) inside the query envelope's
 * filters.properties array. PostHog applies these to events before the
 * user's SQL/insight runs, so they are not bypassable via subqueries,
 * aliases, or aggregation.
 *
 * The user's request body is never trusted to set its own tenant filters
 * — any user-supplied app_code or client_code entries are stripped before
 * the resolved values are appended.
 */
public final class HogQLTenantRewriter {

    private static final String KEY_QUERY = "query";
    private static final String KEY_FILTERS = "filters";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_KEY = "key";
    private static final String KEY_VALUE = "value";
    private static final String KEY_TYPE = "type";
    private static final String KEY_OPERATOR = "operator";

    private static final String TENANT_KEY_APP_CODE = "app_code";
    private static final String TENANT_KEY_URL_CLIENT_CODE = "url_client_code";

    private HogQLTenantRewriter() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> rewrite(Map<String, Object> requestBody, String appCode, String urlClientCode) {

        Map<String, Object> body = requestBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestBody);

        Object queryObj = body.get(KEY_QUERY);
        Map<String, Object> query = queryObj instanceof Map ? new LinkedHashMap<>((Map<String, Object>) queryObj)
                : new LinkedHashMap<>();

        Object filtersObj = query.get(KEY_FILTERS);
        Map<String, Object> filters = filtersObj instanceof Map ? new LinkedHashMap<>((Map<String, Object>) filtersObj)
                : new LinkedHashMap<>();

        Object propsObj = filters.get(KEY_PROPERTIES);
        List<Object> properties = propsObj instanceof List ? new ArrayList<>((List<Object>) propsObj)
                : new ArrayList<>();

        properties.removeIf(HogQLTenantRewriter::isTenantProperty);

        properties.add(tenantProperty(TENANT_KEY_APP_CODE, appCode));
        properties.add(tenantProperty(TENANT_KEY_URL_CLIENT_CODE, urlClientCode));

        filters.put(KEY_PROPERTIES, properties);
        query.put(KEY_FILTERS, filters);
        body.put(KEY_QUERY, query);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static boolean isTenantProperty(Object entry) {
        if (!(entry instanceof Map)) return false;
        Object key = ((Map<String, Object>) entry).get(KEY_KEY);
        return TENANT_KEY_APP_CODE.equals(key) || TENANT_KEY_URL_CLIENT_CODE.equals(key);
    }

    private static Map<String, Object> tenantProperty(String key, String value) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put(KEY_KEY, key);
        prop.put(KEY_VALUE, value);
        prop.put(KEY_OPERATOR, "exact");
        prop.put(KEY_TYPE, "event");
        return prop;
    }
}
