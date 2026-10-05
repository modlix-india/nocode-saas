package com.fincity.saas.commons.core.service.connection.appdata;

import com.fincity.saas.commons.core.document.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The installation-wide history retention default, for storages that declare none.
 *
 * Trimming version rows cannot be undone, so the number that decides it does not
 * belong in a compiled constant. An installation that has never looked at its
 * version volumes should be able to deploy at
 *
 * <pre>
 * core:
 *   appdata:
 *     versionRetention:
 *       days: 0
 *       count: 0
 * </pre>
 *
 * keep everything while it watches, and switch retention on once it knows what it
 * is throwing away. {@code 0} is unlimited on both bounds; a storage that sets its
 * own numbers is unaffected either way.
 *
 * One bean rather than a {@code @Value} in each backend: the two would be
 * configured separately, and MySQL and Mongo disagreeing about how long history
 * survives is a difference nobody discovers until they go looking for a version
 * that is not there.
 */
@Component
public class VersionRetentionDefaults {

    private final VersionRetention defaults;

    public VersionRetentionDefaults(
            @Value(VersionRetention.DAYS_PROPERTY) int days, @Value(VersionRetention.COUNT_PROPERTY) int count) {
        this.defaults = VersionRetention.clamped(days, count);
    }

    /** What an unset storage gets. */
    public VersionRetention get() {
        return this.defaults;
    }

    /** The resolved policy for one storage, which is what the write path wants. */
    public VersionRetention forStorage(Storage storage) {
        return VersionRetention.of(storage, this.defaults);
    }
}
