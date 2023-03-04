package com.fincity.saas.ui.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.util.MapWithOrderComparator;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.model.ChecksumObject;

import reactor.core.publisher.Mono;

@Service
public class IndexHTMLService {

	private static final String[] LINK_FIELDS = new String[] { "crossorigin", "href", "hreflang", "media",
	        "referrerpolicy", "rel", "sizes", "title", "type" };

	private static final String[] SCRIPT_FIELDS = new String[] { "async", "type", "crossorigin", "defer", "integrity",
	        "nomodule", "referrerpolicy", "src" };

	private static final String[] META_FIELDS = new String[] { "charset", "name", "http-equiv", "content" };

	public static final String CACHE_NAME_INDEX = "indexCache";

	@Autowired
	private ApplicationService appService;

	@Autowired
	private CacheService cacheService;

	public Mono<ChecksumObject> getIndexHTML(String appCode, String clientCode) {

		return cacheService.cacheValueOrGet(CACHE_NAME_INDEX,
		        () -> FlatMapUtil.flatMapMonoWithNull(() -> appService.read(appCode, appCode, clientCode),
		                app -> this.indexFromApp(app, appCode, clientCode)),
		        appCode, "-", clientCode);
	}

	@SuppressWarnings("unchecked")
	private Mono<ChecksumObject> indexFromApp(Application app, String appCode, String clientCode) {

		Map<String, Object> appProps = app == null ? Map.of() : app.getProperties();

		StringBuilder str = new StringBuilder(
		        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\" /><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" /><title>");

		Object title = appProps.get("title");
		if (title == null)
			title = app == null ? "Error" : app.getAppCode();

		str.append(title);
		str.append("</title>");

		processTagType(str, (Map<String, Object>) appProps.get("links"), "link", LINK_FIELDS);
		processTagType(str, (Map<String, Object>) appProps.get("scripts"), "script", SCRIPT_FIELDS);
		processTagType(str, (Map<String, Object>) appProps.get("metas"), "meta", META_FIELDS);

		str.append("<link rel=\"manifest\" href=\"/" + appCode + "/" + clientCode
		        + "/manifest/manifest.json\" /></head><body><div id=\"app\"></div>");

		// Here the preference will be for the style from the style service.

		str.append("<link rel=\"stylesheet\" href=\"/" + appCode + "/" + clientCode + "/api/ui/style\" />");

		str.append("<script src=\"/js/index.js\"></script></body></html>");

		return Mono.just(new ChecksumObject(str.toString()));
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
