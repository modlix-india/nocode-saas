package com.fincity.saas.ui.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.util.MapWithOrderComparator;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Application;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class IndexHTMLService {

    private static final Logger logger = LoggerFactory.getLogger(IndexHTMLService.class);

    private static final String[] LINK_FIELDS = new String[] { "crossorigin", "href", "hreflang", "media",
            "referrerpolicy", "rel", "sizes", "title", "type" };

    private static final String[] SCRIPT_FIELDS = new String[] { "async", "type", "crossorigin", "defer", "integrity",
            "nomodule", "referrerpolicy", "src" };

    private static final String[] META_FIELDS = new String[] { "charset", "name", "http-equiv", "content" };

    public static final String CACHE_NAME_INDEX = "indexNewCache";

    private static final Map<String, Integer> CODE_PART_PLACES = Map.of("AFTER_HEAD", 0, "BEFORE_HEAD", 1, "AFTER_BODY",
            2, "BEFORE_BODY", 3);

    private static final Map<String, String> ICON_PACK = Map.ofEntries(

            Map.entry("FREE_FONT_AWESOME_ALL",
                    "<link href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_SYMBOLS_OUTLINED",
                    "<link href=\"https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_SYMBOLS/font.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_SYMBOLS_ROUNDED",
                    "<link href=\"https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_SYMBOLS/font.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_SYMBOLS_SHARP",
                    "<link href=\"https://fonts.googleapis.com/css2?family=Material+Symbols+Sharp:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_SYMBOLS/font.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_ICONS_FILLED",
                    "<link href=\"https://fonts.googleapis.com/icon?family=Material+Icons\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_ICONS/font.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_ICONS_OUTLINED",
                    "<link href=\"https://fonts.googleapis.com/icon?family=Material+Icons+Outlined\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_ICONS/font.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_ICONS_ROUNDED",
                    "<link href=\"https://fonts.googleapis.com/icon?family=Material+Icons+Round\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_ICONS/font.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_ICONS_SHARP",
                    "<link href=\"https://fonts.googleapis.com/icon?family=Material+Icons+Sharp\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_ICONS/font.css\" rel=\"stylesheet\" />"),

            Map.entry("MATERIAL_ICONS_TWO_TONE",
                    "<link href=\"https://fonts.googleapis.com/icon?family=Material+Icons+Two+Tone\" rel=\"stylesheet\" /><link href=\"https://cdn.jsdelivr.net/gh/fincity-india/nocode-ui-icon-packs@master/dist/fonts/MATERIAL_ICONS/font.css\" rel=\"stylesheet\" />")

    );

    private static final String DEFAULT_LOADER = "" +
            "<style>\n" +
            "\t._initloaderContainer {\n" +
            "\t\twidth: 100vw;\n" +
            "\t\theight: 100vh;\n" +
            "\t\tdisplay: flex;\n" +
            "\t\tjustify-content: center;\n" +
            "\t\talign-items: center;\n" +
            "\t\tposition: fixed;\n" +
            "\t\tleft:0;\n" +
            "\t\ttop:0;\n" +
            "\t}\n" +
            "\t._initloader {\n" +
            "\t\t\twidth: 2vmax;\n" +
            "\t\t\theight: 2vmax;\n" +
            "\t\t\tborder: 0.5vmin solid rgb(224, 224, 224);\n" +
            "\t\t\tborder-top: 0.5vmin solid rgb(173, 173, 173);\n" +
            "\t\t\tborder-radius: 50%;\n" +
            "\t\t\tanimation: _loaderspin 3s linear infinite;\n" +
            "\t\t}\n" +
            "\t\t@keyframes _loaderspin {\n" +
            "\t\t\t0% {\n" +
            "\t\t\ttransform: rotate(0deg);\n" +
            "\t\t\t}\n" +
            "\t\t\t100% {\n" +
            "\t\t\ttransform: rotate(360deg);\n" +
            "\t\t\t}\n" +
            "\t\t}\n" +
            "</style>\n" +
            "<div class=\"_initloaderContainer\"><div class=\"_initloader\"></div></div>";

    @Value("${ui.cdnHostName:}")
    private String cdnHostName;

    @Value("${ui.cdnStripAPIPrefix:true}")
    private boolean cdnStripAPIPrefix;

    @Value("${ui.cdnReplacePlus:false}")
    private boolean cdnReplacePlus;

    @Value("${ui.cdnResizeOptionsType:none}")
    private String cdnResizeOptionsType;

    private final ApplicationService appService;
    private final CacheService cacheService;
    private final ResourceLoader resourceLoader;
    private final WebClient.Builder webClientBuilder;
    private final Gson gson = new Gson();
    private List<String> cachedEntrypointScripts = null;

    public IndexHTMLService(ApplicationService appService, CacheService cacheService, ResourceLoader resourceLoader,
                           WebClient.Builder webClientBuilder) {
        this.appService = appService;
        this.cacheService = cacheService;
        this.resourceLoader = resourceLoader;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Load the asset manifest from classpath or CDN
     * The manifest contains the list of webpack chunks to load
     * This is called after dependency injection is complete to ensure @Value fields are initialized
     */
    @PostConstruct
    private void loadAssetManifest() {
        try {
            // Try to load from classpath first (for embedded deployment)
            Resource resource = resourceLoader.getResource("classpath:manifests/asset-manifest.json");

            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String manifestContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    if (parseAndCacheManifest(manifestContent, "classpath")) {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error loading asset manifest from classpath: {}", e.getMessage());
        }

        // Try to load from CDN if configured
        if (this.cdnHostName != null && !this.cdnHostName.isBlank()) {
            try {
                String cdnManifestUrl = "https://" + this.cdnHostName + "/js/dist/asset-manifest.json";
                logger.info("Attempting to load asset manifest from CDN: {}", cdnManifestUrl);

                String manifestContent = webClientBuilder.build()
                    .get()
                    .uri(cdnManifestUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

                if (manifestContent != null && parseAndCacheManifest(manifestContent, "CDN")) {
                    return;
                }
            } catch (Exception e) {
                logger.warn("Error loading asset manifest from CDN: {}", e.getMessage());
            }
        }

        // Fallback to legacy bundles if both classpath and CDN loading fail
        logger.info("Using fallback to legacy bundles (vendors.js, index.js)");
        cachedEntrypointScripts = List.of("vendors.js", "index.js");
    }

    /**
     * Parse the asset manifest JSON and cache the entrypoint scripts
     * @param manifestContent The JSON content of the manifest
     * @param source The source of the manifest (for logging)
     * @return true if parsing was successful, false otherwise
     */
    private boolean parseAndCacheManifest(String manifestContent, String source) {
        try {
            JsonObject manifest = gson.fromJson(manifestContent, JsonObject.class);

            if (manifest.has("entrypoints")) {
                JsonObject entrypoints = manifest.getAsJsonObject("entrypoints");
                if (entrypoints.has("index")) {
                    JsonArray indexArray = entrypoints.getAsJsonArray("index");
                    cachedEntrypointScripts = new ArrayList<>();
                    for (JsonElement element : indexArray) {
                        cachedEntrypointScripts.add(element.getAsString());
                    }
                    logger.info("Asset manifest loaded successfully from {} with {} entrypoint scripts",
                        source, cachedEntrypointScripts.size());
                    return true;
                }
            }

            logger.warn("Invalid manifest structure from {}, missing entrypoints.index", source);
        } catch (Exception e) {
            logger.error("Error parsing asset manifest from {}: {}", source, e.getMessage());
        }
        return false;
    }

    /**
     * Get the list of entrypoint scripts from the manifest
     * Falls back to legacy bundles if manifest is not available
     */
    private List<String> getEntrypointScripts() {
        if (cachedEntrypointScripts == null) {
            return List.of("vendors.js", "index.js");
        }
        return cachedEntrypointScripts;
    }

    public Mono<ObjectWithUniqueID<String>> getIndexHTML(String appCode, String clientCode) {

        String cacheName = this.appService.getCacheName(appCode + "_" + CACHE_NAME_INDEX, appCode);

        return cacheService.<ObjectWithUniqueID<String>>cacheValueOrGet(cacheName,

                () -> FlatMapUtil
                        .flatMapMonoWithNull(

                                () -> appService.read(appCode, appCode, clientCode),

                                app -> this.indexFromApp(app == null ? null : new Application(app.getObject()), appCode,
                                        clientCode))
                        .contextWrite(Context.of(LogUtil.METHOD_NAME,
                                "IndexHTMLService.getIndexHTML (without HTML cache)")),

                clientCode);
    }

    @SuppressWarnings("unchecked")
    private List<String> processCodeParts(Map<String, Object> codeParts) {

        if (codeParts == null || codeParts.isEmpty())
            return List.of("", "", "", "");

        // noinspection ConstantConditions
        List<Tuple2<String, String>> cps = codeParts.values()
                .stream()
                .filter(Objects::nonNull)
                .map(e -> (Map<String, Object>) e)
                .filter(Predicate.not(Map::isEmpty))
                .sorted(new MapWithOrderComparator())
                .filter(e -> !StringUtil.safeIsBlank(e.get("part")))
                .filter(e -> !StringUtil.safeIsBlank(e.get("place")))
                .map(e -> Tuples.of(StringUtil.safeValueOf(e.get("place")), StringUtil.safeValueOf(e.get("part"))))
                .toList();

        List<String> stringCps = new ArrayList<>();

        for (int i = 0; i < 4; i++)
            stringCps.add("");

        for (var tup : cps) {
            if (!CODE_PART_PLACES.containsKey(tup.getT1()))
                continue;

            int index = CODE_PART_PLACES.get(tup.getT1());
            stringCps.set(index, stringCps.get(index) + tup.getT2());
        }

        return stringCps;
    }

    @SuppressWarnings("unchecked")
    private Mono<ObjectWithUniqueID<String>> indexFromApp(Application app, String appCode,
            String clientCode) {

        Map<String, Object> appProps = app == null ? Map.of() : app.getProperties();

        List<String> codeParts = processCodeParts((Map<String, Object>) appProps.get("codeParts"));

        StringBuilder str = new StringBuilder("<!DOCTYPE html><html lang=\"en\"><head>");
        str.append(codeParts.getFirst());
        str.append(
                "<meta charset=\"utf-8\" /><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" /><title>");

        Object title = appProps.get("title");
        if (title == null)
            title = app == null ? "Error" : app.getAppCode();

        str.append(title);
        str.append("</title>");

        processTagType(str, (Map<String, Object>) appProps.get("links"), "link", LINK_FIELDS);
        processTagType(str, (Map<String, Object>) appProps.get("scripts"), "script", SCRIPT_FIELDS);
        processTagType(str, (Map<String, Object>) appProps.get("metas"), "meta", META_FIELDS);

        if (appProps.get("manifest") != null && !((Map<String, ?>) appProps.get("manifest")).isEmpty())
            str.append("<link rel=\"manifest\" href=\"")
                    .append(appCode)
                    .append("/")
                    .append(clientCode)
                    .append("/manifest/manifest.json\" />");
        str.append(processFontPacks((Map<String, Object>) appProps.get("fontPacks")));
        str.append(processIconPacks((Map<String, Object>) appProps.get("iconPacks")));
        str.append(codeParts.get(1));
        str.append("</head><body>");
        str.append(codeParts.get(2));
        str.append("<div id=\"app\">");
        str.append(appProps.getOrDefault("loader", DEFAULT_LOADER));
        str.append("</div>");

        // Here the preference will be for the style from the style service.
        str.append("<link rel=\"stylesheet\" href=\"/")
                .append(appCode)
                .append("/")
                .append(clientCode)
                .append("/page/api/ui/style\" />");
        str.append("<script>");

        if (this.cdnHostName != null && !this.cdnHostName.isBlank()) {
            str.append("window.cdnPrefix='").append(this.cdnHostName).append("';");
            str.append("window.cdnStripAPIPrefix='").append(this.cdnStripAPIPrefix).append("';");
            str.append("window.cdnReplacePlus=").append(this.cdnReplacePlus).append(";");
            str.append("window.cdnResizeOptionsType='").append(this.cdnResizeOptionsType).append("';");
        }

        str.append("window.domainAppCode='").append(appCode).append("';");
        str.append("window.domainClientCode='").append(clientCode).append("';");

        str.append("</script>");

        String jsURLPrefix = (this.cdnHostName == null || this.cdnHostName.isBlank())
            ? "/js/dist/"
            : ("https://" + this.cdnHostName + "/js/dist/");

        // Load entrypoint scripts from manifest (with fallback to legacy bundles)
        List<String> entrypointScripts = getEntrypointScripts();
        for (String script : entrypointScripts) {
            str.append("<script src=\"").append(jsURLPrefix).append(script).append("\"></script>");
        }

        str.append(codeParts.get(3));
        str.append("</body></html>");

        return Mono.just(new ObjectWithUniqueID<>(str.toString()).setHeaders(processCSPHeaders(appProps)));
    }

    @SuppressWarnings("unchecked")
    private String processIconPacks(Map<String, Object> map) {

        if (map == null || map.isEmpty())
            return "";

        return map.values()
                .stream()
                .map(e -> {
                    if (e == null)
                        return "";

                    Map<String, Object> mso = (Map<String, Object>) e;
                    String packName = mso.get("name") == null ? null : ICON_PACK.get(mso.get("name").toString());

                    // noinspection ConstantConditions
                    return CommonsUtil.nonNullValue(mso.get("code"), packName, "")
                            .toString();
                })
                .filter(e -> !StringUtil.safeIsBlank(e))
                .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    private String processFontPacks(Map<String, Object> map) {

        if (map == null || map.isEmpty())
            return "";

        return map.values()
                .stream()
                .map(e -> {
                    if (e == null)
                        return "";

                    Map<String, Object> mso = (Map<String, Object>) e;

                    return StringUtil.safeValueOf(mso.get("code"), "");
                })
                .filter(e -> !StringUtil.safeIsBlank(e))
                .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> processCSPHeaders(Map<String, Object> appProps) {

        if (appProps == null || appProps.isEmpty())
            return null; // NOSONAR
        // Null is the best reply instead of an empty map as it caches.

        Map<String, String> cspHeaders = new HashMap<>();

        String cspString;

        cspString = processCSP((Map<String, String>) appProps.get("csp"));
        if (cspString != null)
            cspHeaders.put("Content-Security-Policy", cspString);

        cspString = processCSP((Map<String, String>) appProps.get("cspReport"));
        if (cspString != null)
            cspHeaders.put("Content-Security-Policy-Report-Only", cspString);

        return cspHeaders.isEmpty() ? null : cspHeaders;
    }

    private String processCSP(Map<String, String> csp) {

        if (csp == null || csp.isEmpty())
            return null;

        StringBuilder cspString = new StringBuilder();

        for (Entry<String, String> e : csp.entrySet()) {

            String key = e.getKey();

            StringBuilder sb = new StringBuilder(key);
            for (int i = 0; i < sb.length(); i++) {
                char c = sb.charAt(i);
                if (!Character.isUpperCase(c))
                    continue;
                sb.setCharAt(i, Character.toLowerCase(c));
                sb.insert(i, '-');
            }

            String value = e.getValue();

            if (this.cdnHostName != null && !this.cdnHostName.isBlank()) {
                if (!StringUtil.safeIsBlank(value))
                    value += " " + this.cdnHostName;
                else
                    value = this.cdnHostName;
            }

            if (!StringUtil.safeIsBlank(value)) {
                cspString.append(sb)
                        .append(' ')
                        .append(value)
                        .append(';');
            }
        }

        return cspString.toString();
    }

    @SuppressWarnings("unchecked")
    private void processTagType(StringBuilder str, Map<String, Object> tagType, String tag, String[] attributeList) {

        if (tagType == null || tagType.isEmpty())
            return;

        str.append(tagType.values()
                .stream()
                .map(e -> (Map<String, Object>) e)
                .sorted(new MapWithOrderComparator())
                .map(e -> this.toTagString(tag, e, attributeList))
                .collect(Collectors.joining()));
    }

    private String toTagString(String tag, Map<String, Object> attributes, String[] attributeList) {

        StringBuilder linkSB = new StringBuilder("<").append(tag)
                .append(' ');

        for (String attr : attributeList)
            if (attributes.containsKey(attr))
                linkSB.append(attr)
                        .append('=')
                        .append("\"")
                        .append(attributes.get(attr))
                        .append("\"");

        return linkSB.append("/> \n")
                .toString();
    }
}
