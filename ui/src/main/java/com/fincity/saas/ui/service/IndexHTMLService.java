package com.fincity.saas.ui.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.util.MapWithOrderComparator;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.model.ChecksumObject;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class IndexHTMLService {

	private static final String[] LINK_FIELDS = new String[] { "crossorigin", "href", "hreflang", "media",
			"referrerpolicy", "rel", "sizes", "title", "type" };

	private static final String[] SCRIPT_FIELDS = new String[] { "async", "type", "crossorigin", "defer", "integrity",
			"nomodule", "referrerpolicy", "src" };

	private static final String[] META_FIELDS = new String[] { "charset", "name", "http-equiv", "content" };

	public static final String CACHE_NAME_INDEX = "indexCache";

	private static final Map<String, Integer> CODE_PART_PLACES = Map.of("AFTER_HEAD", 0, "BEFORE_HEAD", 1, "AFTER_BODY",
			2, "BEFORE_BODY", 3);
	
	  private static final Map<String, String> CSP_HEADERS = Map.ofEntries(

	            Map.entry("default-src", "default-src"),
	            Map.entry("defaultSrc", "default-src"),
	            Map.entry("child-src", "child-src"),
	            Map.entry("childSrc", "child-src"),
	            Map.entry("connect-src", "connect-src"),
	            Map.entry("connectSrc", "connect-src"),
	            Map.entry("font-src", "font-src"),
	            Map.entry("fontSrc", "font-src"),
	            Map.entry("frame-src", "frame-src"),
	            Map.entry("frameSrc", "frame-src"),
	            Map.entry("img-src", "img-src"),
	            Map.entry("imgSrc", "img-src"),
	            Map.entry("manifest-src", "manifest-src"),
	            Map.entry("manifestSrc", "manifest-src"),
	            Map.entry("media-src", "media-src"),
	            Map.entry("mediaSrc", "media-src"),
	            Map.entry("object-src", "object-src"),
	            Map.entry("objectSrc", "object-src"),
	            Map.entry("prefetch-src", "prefetch-src"),
	            Map.entry("prefetchSrc", "prefetch-src"),
	            Map.entry("script-src", "script-src"),
	            Map.entry("scriptSrc", "script-src"),
	            Map.entry("script-src-elem", "script-src-elem"),
	            Map.entry("scripSrcElem", "script-src-elem"),
	            Map.entry("script-src-attr", "script-src-attr"),
	            Map.entry("scripSrcAttr", "script-src-attr"),
	            Map.entry("style-src", "style-src"),
	            Map.entry("styleSrc", "style-src"),
	            Map.entry("style-src-elem", "style-src-elem"),
	            Map.entry("styleSrcElem", "style-src-elem"),
	            Map.entry("style-src-attr", "style-src-attr"),
	            Map.entry("styleSrcAttr", "style-src-attr"),
	            Map.entry("worker-src", "worker-src"),
	            Map.entry("workerSrc", "worker-src"));


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

	@Autowired
	private ApplicationService appService;

	@Autowired
	private CacheService cacheService;

	public Mono<ChecksumObject> getIndexHTML(String appCode, String clientCode) {

		return cacheService.cacheValueOrGet(this.appService.getCacheName(appCode + "_" + CACHE_NAME_INDEX, appCode),
				() -> FlatMapUtil
						.flatMapMonoWithNull(() -> appService.read(appCode, appCode, clientCode),
								app -> this.indexFromApp(app, appCode, clientCode))
						.contextWrite(Context.of(LogUtil.METHOD_NAME, "IndexHTMLService.getIndexHTML")),
				clientCode);
	}

	@SuppressWarnings("unchecked")
	private List<String> processCodeParts(Map<String, Object> codeParts) {

		if (codeParts == null || codeParts.isEmpty())
			return List.of("", "", "", "");

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
	private Mono<ChecksumObject> indexFromApp(Application app, String appCode, String clientCode) {

		Map<String, Object> appProps = app == null ? Map.of() : app.getProperties();

		List<String> codeParts = processCodeParts((Map<String, Object>) appProps.get("codeParts"));

		StringBuilder str = new StringBuilder("<!DOCTYPE html><html lang=\"en\"><head>");
		str.append(codeParts.get(0));
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

		str.append("<link rel=\"manifest\" href=\"/" + appCode + "/" + clientCode + "/manifest/manifest.json\" />");
		str.append(processFontPacks((Map<String, Object>) appProps.get("fontPacks")));
		str.append(processIconPacks((Map<String, Object>) appProps.get("iconPacks")));
		str.append(codeParts.get(1));
		str.append("</head><body>");
		str.append(codeParts.get(2));
		str.append("<div id=\"app\"></div>");

		// Here the preference will be for the style from the style service.

		str.append("<link rel=\"stylesheet\" href=\"/" + appCode + "/" + clientCode + "/api/ui/style\" />");

		str.append("<script src=\"/js/index.js\"></script>");
		str.append(codeParts.get(3));
		str.append("</body></html>");

		return Mono.just(new ChecksumObject(str.toString()).setHeaders(processCSPHeaders(appProps)));
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

					return CommonsUtil.nonNullValue(mso.get("code"), ICON_PACK.get(mso.get("name")), "")
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

        Map<String, String> updatedCSP = new HashMap<>();

        csp.entrySet()
                .stream()
                .forEach(e -> {

                    String actualKey = CSP_HEADERS.get(e.getKey());

                    if (updatedCSP.containsKey(actualKey)) {
                        updatedCSP.put(actualKey, updatedCSP.get(actualKey) + " " + e.getValue());
                    } else {
                        updatedCSP.put(actualKey, actualKey + " " + e.getValue());
                    }
                });

        return updatedCSP.values()
                .stream()
                .sorted()
                .collect(Collectors.joining("; "));

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
