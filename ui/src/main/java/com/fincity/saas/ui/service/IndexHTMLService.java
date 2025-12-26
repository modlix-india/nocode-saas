package com.fincity.saas.ui.service;

import java.time.Duration;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.util.MapWithOrderComparator;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Application;

import jakarta.annotation.PostConstruct;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class IndexHTMLService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexHTMLService.class);

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
    private static final String SSR_HTML_START = "<!--SSR_HTML_START-->";
    private static final String SSR_HTML_END = "<!--SSR_HTML_END-->";
    private static final String SSR_STATE_MARKER = "<!--SSR_STATE-->";
    private static final List<String> SSR_FORWARD_HEADERS = List.of("authorization", "cookie");

    @Value("${ui.cdnHostName:}")
    private String cdnHostName;

    @Value("${ui.cdnStripAPIPrefix:true}")
    private boolean cdnStripAPIPrefix;

    @Value("${ui.cdnReplacePlus:false}")
    private boolean cdnReplacePlus;

    @Value("${ui.cdnResizeOptionsType:none}")
    private String cdnResizeOptionsType;

    @Value("${ui.ssr.baseUrl:}")
    private String ssrBaseUrl;

    @Value("${ui.ssr.timeoutMillis:3000}")
    private long ssrTimeoutMillis;

    private final ApplicationService appService;
    private final CacheService cacheService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private WebClient ssrClient;

    public IndexHTMLService(ApplicationService appService, CacheService cacheService, WebClient.Builder builder,
            ObjectMapper objectMapper) {
        this.appService = appService;
        this.cacheService = cacheService;
        this.webClientBuilder = builder;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initializeSsrClient() {
        if (this.ssrBaseUrl == null || this.ssrBaseUrl.isBlank())
            return;
        this.ssrClient = this.webClientBuilder.baseUrl(this.ssrBaseUrl)
                .build();
    }

    public Mono<ObjectWithUniqueID<String>> getIndexHTML(ServerHttpRequest request, String appCode, String clientCode) {

        String cacheName = this.appService.getCacheName(appCode + "_" + CACHE_NAME_INDEX, appCode);

        return cacheService.<ObjectWithUniqueID<String>>cacheValueOrGet(cacheName,

                () -> FlatMapUtil
                        .flatMapMonoWithNull(

                                () -> appService.read(appCode, appCode, clientCode),

                                app -> this.indexFromApp(app == null ? null : new Application(app.getObject()), appCode,
                                        clientCode))
                        .contextWrite(Context.of(LogUtil.METHOD_NAME,
                                "IndexHTMLService.getIndexHTML (without HTML cache)")),

                clientCode)
                .flatMap(html -> this.decorateWithSSR(html, request));
    }

    private Mono<ObjectWithUniqueID<String>> decorateWithSSR(ObjectWithUniqueID<String> cachedHtml,
            ServerHttpRequest request) {

        ObjectWithUniqueID<String> fallback = new ObjectWithUniqueID<>(
                this.applySsrTemplate(cachedHtml.getObject(), null)).setHeaders(cachedHtml.getHeaders());

        if (this.ssrClient == null)
            return Mono.just(fallback);

        return this.requestSsrPayload(request)
                .map(payload -> new ObjectWithUniqueID<>(this.applySsrTemplate(cachedHtml.getObject(), payload))
                        .setHeaders(cachedHtml.getHeaders()))
                .timeout(Duration.ofMillis(this.ssrTimeoutMillis))
                .onErrorResume(ex -> {
                    LOGGER.error("Unable to fetch SSR fragment", ex);
                    return Mono.just(fallback);
                })
                .switchIfEmpty(Mono.just(fallback));
    }

    private Mono<SsrRenderPayload> requestSsrPayload(ServerHttpRequest request) {
        if (this.ssrClient == null)
            return Mono.empty();

        return this.ssrClient.post()
                .uri("/ssr/page")
                .headers(headers -> copyForwardHeaders(request, headers))
                .bodyValue(Map.of("url", request.getURI().toString()))
                .retrieve()
                .bodyToMono(SsrRenderPayload.class);
    }

    private void copyForwardHeaders(ServerHttpRequest request, HttpHeaders outboundHeaders) {
        SSR_FORWARD_HEADERS.forEach(header -> {
            List<String> values = request.getHeaders().get(header);
            if (values != null && !values.isEmpty())
                outboundHeaders.put(header, values);
        });
    }

    private String applySsrTemplate(String template, SsrRenderPayload payload) {
        String result = replaceSection(template, SSR_HTML_START, SSR_HTML_END, payload == null ? null : payload.html());
        if (payload != null && payload.state() != null)
            result = result.replace(SSR_STATE_MARKER, buildBootstrapScript(payload.state()));
        else
            result = result.replace(SSR_STATE_MARKER, "");
        return result;
    }

    private String replaceSection(String source, String startMarker, String endMarker, String replacement) {
        int start = source.indexOf(startMarker);
        if (start == -1)
            return source;
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (end == -1)
            return source;
        String before = source.substring(0, start);
        String between = source.substring(start + startMarker.length(), end);
        String after = source.substring(end + endMarker.length());
        return before + (replacement == null ? between : replacement) + after;
    }

    private String buildBootstrapScript(Map<String, Object> state) {
        try {
            String json = this.objectMapper.writeValueAsString(state);
            return "<script>window.__APP_BOOTSTRAP__=" + escapeForScript(json) + ";</script>";
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to serialize SSR state", e);
            return "";
        }
    }

    private String escapeForScript(String json) {
        return json.replace("</", "<\\/")
                .replace("<", "\\u003C")
                .replace(">", "\\u003E")
                .replace("&", "\\u0026");
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
        str.append(SSR_HTML_START);
        str.append(appProps.getOrDefault("loader", DEFAULT_LOADER));
        str.append(SSR_HTML_END);
        str.append("</div>");

        // Here the preference will be for the style from the style service.
        str.append("<link rel=\"stylesheet\" href=\"/")
                .append(appCode)
                .append("/")
                .append(clientCode)
                .append("/page/api/ui/style\" />");
        str.append("<script>");

        if (!this.cdnHostName.isBlank()) {
            str.append("window.cdnPrefix='").append(this.cdnHostName).append("';");
            str.append("window.cdnStripAPIPrefix='").append(this.cdnStripAPIPrefix).append("';");
            str.append("window.cdnReplacePlus=").append(this.cdnReplacePlus).append(";");
            str.append("window.cdnResizeOptionsType='").append(this.cdnResizeOptionsType).append("';");
        }

        str.append("window.domainAppCode='").append(appCode).append("';");
        str.append("window.domainClientCode='").append(clientCode).append("';");

        str.append("</script>");
        str.append(SSR_STATE_MARKER);

        String jsURLPrefix = this.cdnHostName.isBlank() ? "/js/dist/" : ("https://" + this.cdnHostName + "/js/dist/");
        str.append("<script src=\"").append(jsURLPrefix).append("index.js")
                .append("\"></script>");
        str.append("<script src=\"").append(jsURLPrefix).append("vendors.js")
                .append("\"></script>");
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

            if (!StringUtil.safeIsBlank(value))
                value += " " + this.cdnHostName;
            else
                value = this.cdnHostName;

            cspString.append(sb)
                    .append(' ')
                    .append(value)
                    .append(';');
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

	private static final class SsrRenderPayload {
		private final String html;
		private final Map<String, Object> state;

		private SsrRenderPayload(String html, Map<String, Object> state) {
			this.html = html;
			this.state = state;
		}

		public String html() {
			return this.html;
		}

		public Map<String, Object> state() {
			return this.state;
		}
	}
}
