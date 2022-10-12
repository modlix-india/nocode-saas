package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Style;
import com.fincity.saas.ui.repository.StyleRepository;
import com.fincity.saas.ui.styles.expression.StyleExpressionEvaluator;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class StyleService extends AbstractAppbasedUIService<Style, StyleRepository> {

	private static final String WIDE_SCREEN = "wideScreen";
	/* Minimum width 1281px */

	private static final String DESKTOP_SCREEN = "desktopScreen";
	/* Minimum width 1025px */

	private static final String TABLET_LANDSCAPE_SCREEN = "tabletLandscapeScreen";
	/* Minimum width 961px */

	private static final String TABLET_POTRAIT_SCREEN = "tabletPotraitScreen";
	/* Minimum width 641px */

	private static final String MOBILE_WIDE_SCREEN = "mobileWideScreen";
	/* Minimum width 481px */

	private static final String MOBILE_SCREEN = "mobileScreen";
	/* Minimum width 320px */

	private static final String DESKTOP_SCREEN_ONLY = "desktopScreenOnly";
	/* Minimum width 1025px - 1280px */

	private static final String TABLET_LANDSCAPE_SCREEN_ONLY = "tabletLandscapeScreenOnly";
	/* Minimum width 961px - 1024px */

	private static final String TABLET_POTRAIT_SCREEN_ONLY = "tabletPotraitScreenOnly";
	/* Minimum width 641px - 960px */

	private static final String MOBILE_WIDE_SCREEN_ONLY = "mobileWideScreenOnly";
	/* Minimum width 481px - 640px */

	private static final String MOBILE_SCREEN_ONLY = "mobileScreenOnly";
	/* Minimum width 320px - 480px */

	private static final String GENERAL = "general";
	// For all resolutions.

	private static final String[] RESOLUTION_ORDER = new String[] { GENERAL, MOBILE_SCREEN, MOBILE_WIDE_SCREEN,
	        TABLET_POTRAIT_SCREEN, TABLET_LANDSCAPE_SCREEN, DESKTOP_SCREEN, WIDE_SCREEN, DESKTOP_SCREEN_ONLY,
	        TABLET_LANDSCAPE_SCREEN_ONLY, TABLET_POTRAIT_SCREEN_ONLY, MOBILE_WIDE_SCREEN_ONLY, MOBILE_SCREEN_ONLY };

	private static final Map<String, Tuple2<Integer, Integer>> RESOLUTIONS = Map.ofEntries(

	        Map.entry(WIDE_SCREEN, Tuples.of(1281, -1)),

	        Map.entry(DESKTOP_SCREEN, Tuples.of(1025, -1)), Map.entry(TABLET_LANDSCAPE_SCREEN, Tuples.of(961, -1)),
	        Map.entry(TABLET_POTRAIT_SCREEN, Tuples.of(641, -1)), Map.entry(MOBILE_WIDE_SCREEN, Tuples.of(481, -1)),
	        Map.entry(MOBILE_SCREEN, Tuples.of(320, -1)),

	        Map.entry(DESKTOP_SCREEN_ONLY, Tuples.of(1025, 1280)),
	        Map.entry(TABLET_LANDSCAPE_SCREEN_ONLY, Tuples.of(961, 1024)),
	        Map.entry(TABLET_POTRAIT_SCREEN_ONLY, Tuples.of(641, 960)),
	        Map.entry(MOBILE_WIDE_SCREEN_ONLY, Tuples.of(481, 640)),
	        Map.entry(MOBILE_SCREEN_ONLY, Tuples.of(320, 480)));

	private static final String INHERIT = "inherit";
	private static final String MODES = "modes";
	private static final String NON_RESOLUTION = "nonResolution";
	private static final String PARTS = "parts";

	protected StyleService() {
		super(Style.class);
	}

	@Override
	protected Mono<Style> updatableEntity(Style entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                UIMessageResourceService.VERSION_MISMATCH);

			        existing.setStyles(entity.getStyles())
			                .setVariables(entity.getVariables())
			                .setVariableGroups(entity.getVariableGroups());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

	public Mono<String> readCSS(String themeName, String appCode, String clientCode) {

		return this.read(themeName, appCode, clientCode)
		        .map(this::makeCss)
		        .defaultIfEmpty("");
	}

	private String makeCss(Style theme) {

		if (theme == null)
			return "";
		StringBuilder sb = new StringBuilder("/* CSS from Theme : ").append(theme.getName())
		        .append("*/\n");

		if (theme.getStyles() == null || theme.getStyles()
		        .isEmpty())
			return sb.toString();

		var levelMap = theme.getStyles()
		        .entrySet()
		        .stream()
		        .filter(e -> Objects.nonNull(e.getValue()) && !e.getValue()
		                .isEmpty())
		        .collect(Collectors.toMap(Entry::getKey, e -> Tuples.of(StringUtil.safeValueOf(e.getValue()
		                .get(INHERIT), ""), 0)));

		levelMap.keySet()
		        .stream()
		        .forEach(e -> assignLevelNumberRecursively(levelMap, e));

		final Map<String, Map<String, String>> variableMap = theme.getVariables() == null ? Map.of()
		        : theme.getVariables();

		final Map<String, String> dv = StyleExpressionEvaluator.evaluateVaribles(variableMap.entrySet()
		        .stream()
		        .map(e -> Tuples.of(e.getKey(), e.getValue() != null ? e.getValue()
		                .getOrDefault("value", "") : ""))
		        .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2)));

		Map<String, MultiValueMap<String, String>> resolutionMap = new LinkedHashMap<>();
		Map<String, Map<String, String>> nonResolutionMap = new LinkedHashMap<>();

		theme.getStyles()
		        .entrySet()
		        .stream()
		        .filter(e -> Objects.nonNull(e.getValue()) && !e.getValue()
		                .isEmpty())
		        .sorted((a, b) -> Integer.compare(levelMap.get(a.getKey())
		                .getT2(),
		                levelMap.get(b.getKey())
		                        .getT2()))
		        .forEach(e -> convertEachValue(e, resolutionMap, nonResolutionMap, dv));

		nonResolutionMap.entrySet()
		        .stream()
		        .map(this::getNonResolutionMapLines)
		        .forEach(sb::append);
		sb.append("\n");
		getCSSWithMediaQueries(resolutionMap).forEach(sb::append);

		return sb.toString();
	}

	private void assignLevelNumberRecursively(Map<String, Tuple2<String, Integer>> levelMap, String e) {

		if (levelMap.get(e) == null)
			return;

		if (levelMap.get(e)
		        .getT1()
		        .isBlank())
			return;

		String parent = levelMap.get(e)
		        .getT1();
		if (!levelMap.containsKey(parent))
			return;

		if (levelMap.get(parent)
		        .getT2() == 0)
			assignLevelNumberRecursively(levelMap, parent);

		levelMap.put(e, levelMap.get(e)
		        .mapT2(v -> levelMap.get(parent)
		                .getT2() + 1));
	}

	@SuppressWarnings("unchecked")
	private void convertEachValue(Entry<String, Map<String, Object>> cssMap,
	        Map<String, MultiValueMap<String, String>> resolutionMap, Map<String, Map<String, String>> nonResolutionMap,
	        final Map<String, String> dv) {

		if (cssMap.getValue()
		        .get(NON_RESOLUTION) != null && BooleanUtil.safeValueOf(
		                cssMap.getValue()
		                        .get(NON_RESOLUTION))) {

			Map<String, Map<String, String>> parts = (Map<String, Map<String, String>>) cssMap.getValue()
			        .get(PARTS);

			if (parts != null && !parts.isEmpty())
				nonResolutionMap.put(cssMap.getKey(), parts.entrySet()
				        .stream()
				        .collect(Collectors.toMap(Entry::getKey, e -> convertEachModeToCSS(e.getValue(), dv))));
			return;
		}

		if (cssMap.getValue()
		        .containsKey(INHERIT)) {

			final String inheritedFrom = cssMap.getValue()
			        .get(INHERIT)
			        .toString();
			resolutionMap.keySet()
			        .stream()
			        .filter(resolutionMap::containsKey)
			        .filter(e -> resolutionMap.get(e)
			                .containsKey(inheritedFrom))
			        .forEach(e -> resolutionMap.get(e)
			                .get(inheritedFrom)
			                .stream()
			                .forEach(i -> resolutionMap.get(e)
			                        .add(cssMap.getKey(), i)));
		}

		if (!cssMap.getValue()
		        .containsKey(MODES))
			return;

		Map<String, Map<String, String>> modes = (Map<String, Map<String, String>>) cssMap.getValue()
		        .get(MODES);
		modes.keySet()
		        .stream()
		        .forEach(e ->
				{

			        if (!resolutionMap.containsKey(e))
				        resolutionMap.put(e, new LinkedMultiValueMap<>());

			        resolutionMap.get(e)
			                .add(cssMap.getKey(), convertEachModeToCSS(modes.get(e), dv));
		        });
	}

	private String convertEachModeToCSS(Map<String, String> map, final Map<String, String> defaultValues) {

		return map.entrySet()
		        .stream()
		        .map(e ->
				{
			        var value = StyleExpressionEvaluator.evaluateExpression(e.getValue(), defaultValues);
			        if (value == null)
				        return null;
			        return e.getKey() + " : " + value;
		        })
		        .filter(Objects::nonNull)
		        .collect(Collectors.joining(";", "{", "}"));
	}

	private StringBuilder getNonResolutionMapLines(Entry<String, Map<String, String>> entry) {

		return new StringBuilder(entry.getKey()).append(" {")
		        .append(entry.getValue()
		                .entrySet()
		                .stream()
		                .map(e -> new StringBuilder(e.getKey()).append(e.getValue())
		                        .toString())
		                .collect(Collectors.joining("")))
		        .append("}");
	}

	private List<String> getCSSWithMediaQueries(Map<String, MultiValueMap<String, String>> resolutionMap) {

		return Arrays.stream(RESOLUTION_ORDER)
		        .filter(resolutionMap::containsKey)
		        .map(e ->
				{
			        var yes = RESOLUTIONS.containsKey(e);
			        var start = "";
			        if (yes)
				        start = "@media " + mediaQuery(e) + " {";
			        return start + convertEachMapIntoCSS(resolutionMap.get(e)) + (yes ? " } " : "");
		        })
		        .toList();
	}

	private String convertEachMapIntoCSS(MultiValueMap<String, String> map) {

		return map.keySet()
		        .stream()
		        .map(e -> map.get(e)
		                .stream()
		                .map(i -> e + " " + i)
		                .collect(Collectors.joining(" ", " ", " ")))
		        .collect(Collectors.joining());
	}

	private String mediaQuery(String e) {

		Tuple2<Integer, Integer> x = RESOLUTIONS.get(e);
		String query = "(min-width: " + x.getT1() + "px)";
		if (x.getT2() == -1)
			return query;

		return query + " and (max-width: " + x.getT2() + "px)";
	}
}
