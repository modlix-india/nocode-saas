package com.fincity.saas.ui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.ui.document.Application;

/**
 * The order in which a theme is tried. A `pageOnly` theme is one only a page names
 * (page `properties.theme`): it is served when asked for by name and never as the
 * default. themeSelection.ts in the client and api/client.ts in the SSR renderer
 * carry the same rule and must agree.
 */
class ThemeCandidatesTest {

    @SafeVarargs
    private static Application app(Map<String, Object>... entries) {
        Map<String, Object> themes = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i++)
            themes.put("t" + i, entries[i]);
        Application app = new Application();
        app.setProperties(new LinkedHashMap<>(Map.of("themes", themes)));
        return app;
    }

    private static Map<String, Object> theme(String name, int order, boolean pageOnly) {
        Map<String, Object> e = new LinkedHashMap<>(Map.of("name", name, "order", order));
        if (pageOnly)
            e.put("pageOnly", true);
        return e;
    }

    private static List<Object> names(List<Map<String, Object>> entries) {
        return entries.stream()
                .map(e -> e.get("name"))
                .toList();
    }

    private final Application withClassic = app(theme("monoLight", 1, false), theme("monoDark", 2, false),
            theme("classic", 0, true));

    @Test
    void aPageOnlyThemeIsNeverTheDefaultEvenWithTheLowestOrder() {
        assertEquals(List.of("monoLight", "monoDark"), names(EngineService.themeCandidates(withClassic, null)));
    }

    @Test
    void aPageOnlyThemeIsServedWhenAskedForByName() {
        assertEquals(List.of("classic", "monoLight", "monoDark"),
                names(EngineService.themeCandidates(withClassic, "classic")));
    }

    @Test
    void anUnlistedRequestFallsBackToTheVisitorThemes() {
        assertEquals(List.of("monoLight", "monoDark"), names(EngineService.themeCandidates(withClassic, "gone")));
    }

    @Test
    void anAppListingOnlyPageOnlyThemesIsStillThemed() {
        assertEquals(List.of("classic"), names(EngineService.themeCandidates(app(theme("classic", 0, true)), null)));
    }
}
