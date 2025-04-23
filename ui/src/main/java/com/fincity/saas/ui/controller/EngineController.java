package com.fincity.saas.ui.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.util.MapWithOrderComparator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.MergeMapUtil;
import com.fincity.saas.ui.utils.ResponseEntityUtils;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.document.UIFunction;
import com.fincity.saas.ui.service.ApplicationService;
import com.fincity.saas.ui.service.PageService;
import com.fincity.saas.ui.service.StyleService;
import com.fincity.saas.ui.service.StyleThemeService;
import com.fincity.saas.ui.service.UIFunctionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@RestController
@RequestMapping("api/ui/")
public class EngineController {

	private final ApplicationService appService;

	private final PageService pageService;

	private final StyleService styleService;

	private final StyleThemeService themeService;

	private final UIFunctionService functionService;

	public EngineController(ApplicationService appService, PageService pageService, StyleService styleService,
			StyleThemeService themeService, UIFunctionService functionService) {
		this.appService = appService;
		this.pageService = pageService;
		this.styleService = styleService;
		this.themeService = themeService;
		this.functionService = functionService;
	}

	@Value("${ui.resourceCacheAge:604800}")
	private int cacheAge;

	private static final ResponseEntity<Application> APPLICATION_NOT_FOUND = ResponseEntity
			.notFound()
			.build();

	private static final ResponseEntity<Page> PAGE_NOT_FOUND = ResponseEntity
			.notFound()
			.build();

	private static final ResponseEntity<UIFunction> FUNCTION_NOT_FOUND = ResponseEntity
			.notFound()
			.build();

	@GetMapping("application")
	public Mono<ResponseEntity<Application>> application(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return this.appService.read(appCode, appCode, clientCode)
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(APPLICATION_NOT_FOUND);
	}

	@GetMapping("page/{pageName}")
	public Mono<ResponseEntity<Page>> page(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode, @PathVariable("pageName") String pageName,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return this.pageService.read(pageName, appCode, clientCode)
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(PAGE_NOT_FOUND);
	}

	@SuppressWarnings("unchecked")
	@GetMapping(value = "style", produces = { "text/css" })
	public Mono<ResponseEntity<String>> style(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return FlatMapUtil.flatMapMono(

				() -> this.appService.read(appCode, appCode, clientCode),

				appObject -> {

					var app = appObject.getObject();

					if (app.getProperties() == null || app.getProperties()
							.isEmpty())
						return Mono.just(List.<String>of());

					Map<String, Map<String, Object>> styles = (Map<String, Map<String, Object>>) app.getProperties()
							.get("styles");

					if (styles == null || styles.isEmpty())
						return Mono.just(List.<String>of());

					return Mono.just(stylesThemesFromProps(styles));
				},
				(app, styles) -> {

					if (styles == null || styles.isEmpty())
						return Mono.just(new ObjectWithUniqueID<>("", app.getUniqueId()));

					return Flux.fromIterable(styles)
							.flatMap(e -> this.styleService.read(e, appCode, clientCode))
							.collectList()
							.flatMap(lst -> {
								if (lst == null || lst.isEmpty())
									return Mono.just(new ObjectWithUniqueID<>("", app.getUniqueId()));

								if (lst.size() == 1)
									return Mono.just(new ObjectWithUniqueID<>(lst.get(0).getObject().getStyleString(),
											lst.get(0).getUniqueId() + app.getUniqueId()));
								StringBuilder finString = new StringBuilder(lst.get(0).getObject().getStyleString());
								StringBuilder sb = new StringBuilder();

								for (int i = 1; i < lst.size(); i++) {
									finString.append("\n");
									finString.append(lst.get(i).getObject().getStyleString());
									sb.append(lst.get(i).getUniqueId());
								}

								sb.append(app.getUniqueId());

								return Mono.just(new ObjectWithUniqueID<>(finString.toString(), sb.toString()));
							})
							.defaultIfEmpty(new ObjectWithUniqueID<>("", app.getUniqueId()))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.style inner"));
				},

				(app, styles, theme) -> ResponseEntityUtils.makeResponseEntity(theme, eTag, cacheAge))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.style"));
	}

	@SuppressWarnings("unchecked")
	@GetMapping(value = "theme")
	public Mono<ResponseEntity<Map<String, Map<String, String>>>> theme(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode,
			@RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return FlatMapUtil.flatMapMono(

				() -> this.appService.read(appCode, appCode, clientCode),

				appObject -> {

					var app = appObject.getObject();

					if (app.getProperties() == null || app.getProperties()
							.isEmpty())
						return Mono.just(List.<String>of());

					Map<String, Map<String, Object>> styles = (Map<String, Map<String, Object>>) app.getProperties()
							.get("themes");

					if (styles == null || styles.isEmpty())
						return Mono.just(List.<String>of());

					return Mono.just(stylesThemesFromProps(styles));
				},
				(app, styles) -> {

					if (styles == null || styles.isEmpty())
						return Mono.just(new ObjectWithUniqueID<>(Map.of(), app.getUniqueId()));

					return Flux.fromIterable(styles)
							.flatMap(e -> this.themeService.read(e, appCode, clientCode))
							.collectList()
							.flatMap(lst -> {
								if (lst == null || lst.isEmpty())
									return Mono.just(new ObjectWithUniqueID<>(Map.<String, Map<String, String>>of(),
											app.getUniqueId()));

								if (lst.size() == 1)
									return Mono.just(new ObjectWithUniqueID<>(lst.get(0).getObject().getVariables(),
											lst.get(0).getUniqueId() + app.getUniqueId()));

								Map<String, Map<String, String>> finMap = lst.get(0).getObject().getVariables();
								StringBuilder sb = new StringBuilder();

								for (int i = 1; i < lst.size(); i++) {
									finMap = MergeMapUtil.merge(finMap, lst.get(i).getObject().getVariables());
									sb.append(lst.get(i).getUniqueId());
								}

								sb.append(app.getUniqueId());

								return Mono.just(new ObjectWithUniqueID<>(finMap, sb.toString()));
							})
							.defaultIfEmpty(new ObjectWithUniqueID<>(Map.of(), app.getUniqueId()))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.theme inner"));
				},

				(app, styles, theme) -> ResponseEntityUtils.makeResponseEntity(theme, eTag, cacheAge))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.theme outer"));
	}

	private List<String> stylesThemesFromProps(Map<String, Map<String, Object>> styles) {
		return styles.values()
				.stream()
				.sorted(new MapWithOrderComparator())
				.map(e -> {
					Object styleName = e.get("name");
					if (styleName == null)
						return null;
					return styleName.toString();
				})
				.filter(Objects::nonNull)
				.toList();
	}

	@GetMapping("function/{namespace}/{name}")
	public Mono<ResponseEntity<UIFunction>> function(@RequestHeader("appCode") String appCode,
			@RequestHeader("clientCode") String clientCode, @PathVariable("namespace") String namespace,
			@PathVariable("name") String name, @RequestHeader(name = "If-None-Match", required = false) String eTag) {

		return this.functionService.read(namespace + "." + name, appCode, clientCode)
				.flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
				.defaultIfEmpty(FUNCTION_NOT_FOUND);
	}
}
