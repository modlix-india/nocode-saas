package com.modlix.saas.adzump.compile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.platform.PlatformCompiler;

/**
 * The small P1 lookup J8 uses to resolve a {@link PlatformCompiler} by {@link Platform} while the
 * real {@code AdPlatform} beans (J3 Meta / J4 Google) do not yet exist. Spring injects every
 * {@code PlatformCompiler} bean ({@link MetaCompiler}, {@link GoogleCompiler}) and this indexes them
 * by {@link PlatformCompiler#platform()}.
 *
 * <p>
 * TODO(J3/J4): once {@code MetaPlatform}/{@code GooglePlatform} expose these via
 * {@code AdPlatform.compiler()}, J8 resolves the compiler through {@code AdPlatformRegistry} instead
 * and this bean can be retired.
 * </p>
 */
@Component
public class PlatformCompilerRegistry {

    private final Map<Platform, PlatformCompiler> byPlatform;

    public PlatformCompilerRegistry(List<PlatformCompiler> compilers) {

        Map<Platform, PlatformCompiler> map = new EnumMap<>(Platform.class);
        for (PlatformCompiler compiler : compilers)
            map.put(compiler.platform(), compiler);

        this.byPlatform = map;
    }

    /** Resolves the compiler for a platform, or throws when none is registered. */
    public PlatformCompiler get(Platform platform) {

        PlatformCompiler compiler = this.byPlatform.get(platform);
        if (compiler == null)
            throw new IllegalStateException("No PlatformCompiler registered for " + platform);

        return compiler;
    }

    public boolean has(Platform platform) {
        return this.byPlatform.containsKey(platform);
    }
}
