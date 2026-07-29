package com.modlix.saas.adzump.vertical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Indexes the {@link VerticalPlaybook} beans by {@link VerticalPlaybook#code()} and resolves the
 * playbook for a plan's vertical. An unknown/blank vertical falls back to the {@link #GENERIC}
 * playbook so a product whose vertical A2 could not confidently deduce still builds (with broader
 * validation warnings) — the system stays domain-general from day one.
 */
@Component
public class VerticalRegistry {

    /** Code of the safe fallback playbook. */
    public static final String GENERIC = "generic";

    private static final Logger logger = LoggerFactory.getLogger(VerticalRegistry.class);

    private final Map<String, VerticalPlaybook> byCode;
    private final VerticalPlaybook generic;

    public VerticalRegistry(List<VerticalPlaybook> playbooks) {

        this.byCode = new LinkedHashMap<>();
        for (VerticalPlaybook p : playbooks) {
            VerticalPlaybook prev = this.byCode.put(p.code(), p);
            if (prev != null)
                logger.warn("Duplicate VerticalPlaybook code '{}': {} overrode {}",
                        p.code(), p.getClass().getSimpleName(), prev.getClass().getSimpleName());
        }

        this.generic = this.byCode.get(GENERIC);
        if (this.generic == null)
            logger.error("No '{}' VerticalPlaybook bean registered; unknown-vertical resolution will "
                    + "return null. Registered: {}", GENERIC, this.byCode.keySet());
    }

    /**
     * Resolves the playbook for {@code code}, falling back to the generic playbook (and logging a
     * warning) when the code is null/blank or unknown. Never throws — an unknown vertical must not
     * break plan resolution.
     */
    public VerticalPlaybook get(String code) {

        if (code != null && !code.isBlank()) {
            VerticalPlaybook found = this.byCode.get(code);
            if (found != null)
                return found;
            logger.warn("No VerticalPlaybook for vertical '{}'; falling back to '{}'. Registered: {}",
                    code, GENERIC, this.byCode.keySet());
        }

        return this.generic;
    }

    /**
     * Quiet variant of {@link #get(String)} for hot paths: returns the matching playbook, else the
     * generic fallback, without logging.
     */
    public VerticalPlaybook getOrDefault(String code) {

        if (code == null || code.isBlank())
            return this.generic;

        return this.byCode.getOrDefault(code, this.generic);
    }
}
