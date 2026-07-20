package com.modlix.saas.adzump.platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * Indexes every {@link AdPlatform} bean by its {@link AdPlatform#code()}. Spring injects all
 * implementations, so J3 (Meta) / J4 (Google) register themselves simply by being beans; J7/J8/J10
 * resolve a platform here. A plan targeting Meta + Google fans out to {@code get(META)} and
 * {@code get(GOOGLE)} and merges results onto the one plan.
 *
 * <p>In the P1 offline slice no real {@code AdPlatform} beans exist yet (J3/J4 are later slices and
 * {@code NoopPlatform} lives in test scope), so the injected list is empty and the registry resolves
 * nothing until a platform bean is added.
 */
@Component
public class AdPlatformRegistry {

    private final Map<Platform, AdPlatform> byCode;
    private final AdzumpMessageResourceService msgService;

    public AdPlatformRegistry(List<AdPlatform> platforms, AdzumpMessageResourceService msgService) {

        this.msgService = msgService;

        Map<Platform, AdPlatform> index = new EnumMap<>(Platform.class);
        for (AdPlatform platform : platforms)
            index.put(platform.code(), platform);

        this.byCode = index;
    }

    /**
     * Resolves the platform for a code, or throws PLATFORM_NOT_AVAILABLE (503) when no bean is
     * registered for it.
     */
    public AdPlatform get(Platform code) {

        AdPlatform platform = this.byCode.get(code);

        if (platform == null)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.SERVICE_UNAVAILABLE, msg),
                    AdzumpMessageResourceService.PLATFORM_NOT_AVAILABLE, code);

        return platform;
    }

    /** The platform codes currently backed by a registered bean. */
    public Set<Platform> available() {
        return Collections.unmodifiableSet(this.byCode.keySet());
    }
}
