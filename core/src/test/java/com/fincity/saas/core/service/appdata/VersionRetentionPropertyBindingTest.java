package com.fincity.saas.core.service.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.core.service.connection.appdata.VersionRetention;
import com.fincity.saas.commons.core.service.connection.appdata.VersionRetentionDefaults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * That the retention defaults are configurable is only true if the property names
 * actually bind.
 *
 * {@link VersionRetention}'s own tests cover the policy; they cannot catch a typo
 * in the property name or a value Spring will not convert, and that failure is
 * silent - the bean just keeps the compiled fallback and the installation's
 * setting does nothing. Needs no infrastructure, so it runs with the unit tests.
 */
class VersionRetentionPropertyBindingTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(VersionRetentionDefaults.class);

    @Test
    @DisplayName("An unconfigured installation gets the compiled 90 days / 50 versions")
    void unconfiguredUsesCompiledDefault() {

        this.runner.run(ctx -> {
            VersionRetention r = ctx.getBean(VersionRetentionDefaults.class).get();
            assertEquals(VersionRetention.DEFAULT_DAYS, r.days());
            assertEquals(VersionRetention.DEFAULT_COUNT, r.count());
        });
    }

    @Test
    @DisplayName("Configuration replaces both bounds")
    void configurationBinds() {

        this.runner.withPropertyValues(
                        "core.appdata.versionRetention.days=14", "core.appdata.versionRetention.count=5")
                .run(ctx -> {
                    VersionRetention r = ctx.getBean(VersionRetentionDefaults.class).get();
                    assertEquals(14, r.days());
                    assertEquals(5, r.count());
                });
    }

    /**
     * The reason this is configuration at all: a deployment that has never looked
     * at its version volumes can keep everything until it has, because trimming
     * history cannot be undone.
     */
    @Test
    @DisplayName("0/0 turns retention off for every storage that declares none")
    void configuredOffKeepsEverything() {

        this.runner.withPropertyValues("core.appdata.versionRetention.days=0", "core.appdata.versionRetention.count=0")
                .run(ctx -> assertTrue(ctx.getBean(VersionRetentionDefaults.class)
                        .forStorage(null)
                        .keepsEverything()));
    }

    /**
     * Pinned because it is the mistake someone will make in the YAML. These are
     * read with a {@code @Value} placeholder, which is a literal lookup: the
     * relaxed, dashed spelling Spring Boot accepts for
     * {@code @ConfigurationProperties} silently resolves to nothing here, and the
     * installation keeps the compiled default while the file says otherwise.
     */
    @Test
    @DisplayName("Only the camelCase spelling binds; a dashed key is silently ignored")
    void dashedSpellingDoesNotBind() {

        this.runner.withPropertyValues("core.appdata.version-retention.days=7")
                .run(ctx -> assertEquals(
                        VersionRetention.DEFAULT_DAYS,
                        ctx.getBean(VersionRetentionDefaults.class).get().days()));

        this.runner.withPropertyValues("core.appdata.versionRetention.days=7")
                .run(ctx -> assertEquals(
                        7, ctx.getBean(VersionRetentionDefaults.class).get().days()));
    }
}
