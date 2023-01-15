package com.fincity.saas.ui.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.util.MapWithOrderComparator;
import com.fincity.saas.commons.mongo.util.MergeMapUtil;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.document.Style;
import com.fincity.saas.ui.document.StyleTheme;
import com.fincity.saas.ui.document.UIFunction;
import com.fincity.saas.ui.service.ApplicationService;
import com.fincity.saas.ui.service.PageService;
import com.fincity.saas.ui.service.StyleService;
import com.fincity.saas.ui.service.StyleThemeService;
import com.fincity.saas.ui.service.UIFunctionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/")
public class EngineController {

	@Autowired
	private ApplicationService appService;

	@Autowired
	private PageService pageService;

	@Autowired
	private StyleService styleService;

	@Autowired
	private StyleThemeService themeService;

	@Autowired
	private UIFunctionService functionService;

	@GetMapping("application")
	public Mono<ResponseEntity<Application>> application(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode) {

		return this.appService.read(appCode, appCode, clientCode)
		        .map(ResponseEntity::ok)
		        .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
		                .build())));
	}

	@GetMapping("page/{pageName}")
	public Mono<ResponseEntity<Page>> page(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("pageName") String pageName) {

		return this.pageService.read(pageName, appCode, clientCode)
		        .map(ResponseEntity::ok)
		        .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
		                .build())));
	}

	@SuppressWarnings("unchecked")
	@GetMapping(value = "style", produces = { "text/css" })
	public Mono<ResponseEntity<String>> style(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode) {

		Mono<List<String>> monoStyles = this.appService.read(appCode, appCode, clientCode)
		        .flatMap(app ->
				{

			        if (app.getProperties() == null || app.getProperties()
			                .isEmpty())
				        return Mono.empty();

			        Map<String, Map<String, Object>> styles = (Map<String, Map<String, Object>>) app.getProperties()
			                .get("styles");

			        if (styles == null || styles.isEmpty())
				        return Mono.empty();

			        return stylesThemesFromProps(styles);
		        });

		return monoStyles.flatMapMany(Flux::fromIterable)
		        .flatMap(e -> this.styleService.read(e, appCode, clientCode))
		        .map(Style::getStyleString)
		        .collectList()
		        .map(lst -> lst.stream()
		                .collect(Collectors.joining("\n")))
		        .defaultIfEmpty("")
		        .map(ResponseEntity::ok);
	}
	
	@SuppressWarnings("unchecked")
	@GetMapping(value = "theme")
	public Mono<ResponseEntity<Map<String, Map<String, String>>>> theme(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode) {

		Mono<List<String>> monoStyles = this.appService.read(appCode, appCode, clientCode)
		        .flatMap(app ->
				{

			        if (app.getProperties() == null || app.getProperties()
			                .isEmpty())
				        return Mono.empty();

			        Map<String, Map<String, Object>> styles = (Map<String, Map<String, Object>>) app.getProperties()
			                .get("themes");

			        if (styles == null || styles.isEmpty())
				        return Mono.empty();

			        return stylesThemesFromProps(styles);
		        });

		return monoStyles.flatMapMany(Flux::fromIterable)
		        .flatMap(e -> this.themeService.read(e, appCode, clientCode))
		        .map(StyleTheme::getVariables)
		        .collectList()
		        .flatMap(lst -> {
		        	if (lst == null || lst.isEmpty())
		        		return Mono.empty();
		        	
		        	if (lst.size() == 1)
		        		return Mono.just(lst.get(0));
		        	
		        	Map<String, Map<String, String>> finMap = lst.get(0);
		        	
		        	for (int i = 1; i<lst.size();i++)
		        		finMap = MergeMapUtil.merge(finMap, lst.get(i));
		        	
		        	return Mono.just(finMap);
		        })
		        .defaultIfEmpty(Map.of())
		        .map(ResponseEntity::ok);
	}

	private Mono<? extends List<String>> stylesThemesFromProps(Map<String, Map<String, Object>> styles) {
		List<String> ss = styles.values()
		        .stream()
		        .sorted(new MapWithOrderComparator())
		        .map(e ->
				{
		            Object styleName = e.get("name");
		            if (styleName == null)
		                return null;
		            return styleName.toString();
		        })
		        .filter(Objects::nonNull)
		        .toList();

		if (ss.isEmpty())
		    return Mono.empty();

		return Mono.just(ss);
	}

	@GetMapping("function/{namespace}/{name}")
	public Mono<ResponseEntity<UIFunction>> function(@RequestHeader("appCode") String appCode,
	        @RequestHeader("clientCode") String clientCode, @PathVariable("namespace") String namespace,
	        @PathVariable("name") String name) {

		return this.functionService.read(namespace + "." + name, appCode, clientCode)
		        .map(ResponseEntity::ok)
		        .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
		                .build())));
	}
}
