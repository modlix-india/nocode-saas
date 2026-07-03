package com.modlix.saas.adzump.platform;

/**
 * The neutral run-state a caller (J8 lifecycle) asks a platform to move an entity into.
 * Platform-specific status vocabularies (Meta {@code ACTIVE/PAUSED/ARCHIVED},
 * Google {@code ENABLED/PAUSED/REMOVED}) are mapped inside each {@link AdPlatform} impl.
 */
public enum RunState {
    PAUSE,
    ACTIVE,
    ARCHIVED
}
