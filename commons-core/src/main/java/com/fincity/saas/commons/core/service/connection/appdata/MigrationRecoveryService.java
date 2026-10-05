package com.fincity.saas.commons.core.service.connection.appdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLMigrationSweeper;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.RecoveryReport;

import reactor.core.publisher.Mono;

/**
 * Finishes schema migrations that a restart or a deployment interrupted.
 *
 * Before this, an interrupted migration waited for somebody to publish that storage
 * again. A rolling deploy that killed a node part way through left the tenant
 * half-migrated, with nothing watching and nothing saying so.
 *
 * It runs at startup AND on a timer, and the two are not redundant. Expand-contract
 * ordering means almost every interruption leaves a working table carrying a spare
 * column - untidy, not broken. The exception is the single statement that spans
 * CONTRACT, where the original column has been dropped and the temporary one is not
 * yet renamed: that field cannot be read or written until the rename runs. A node
 * coming back up is the first chance to close that window, so startup is when the
 * sweep matters most; the timer is for the node that was never restarted.
 */
@Service
public class MigrationRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationRecoveryService.class);

    private final ConnectionService connectionService;
    private final MySQLAppDataService mySQLAppDataService;

    /**
     * Off by default.
     *
     * No app is on this backend yet, and a sweep that nothing needs is a timer
     * issuing information_schema queries against every MySQL server the platform
     * knows about. It is turned on with the first tenant provisioned onto MySQL.
     */
    @Value("${core.appdata.mysql.recovery.enabled:false}")
    private boolean enabled;

    @Value("${core.appdata.mysql.recovery.staleAfterSeconds:60}")
    private int staleAfterSeconds;

    public MigrationRecoveryService(
            ConnectionService connectionService, MySQLAppDataService mySQLAppDataService) {
        this.connectionService = connectionService;
        this.mySQLAppDataService = mySQLAppDataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        this.sweep("startup").subscribe();
    }

    @Scheduled(
            initialDelayString = "${core.appdata.mysql.recovery.initialDelayMs:120000}",
            fixedDelayString = "${core.appdata.mysql.recovery.intervalMs:300000}")
    public void onSchedule() {
        this.sweep("scheduled").subscribe();
    }

    /**
     * One pass over every MySQL app-data connection.
     *
     * Never throws at the caller. A sweep is a background repair, and a failure to
     * repair must not take down a node that is otherwise serving perfectly well -
     * especially at startup, where it would turn one stuck migration into an
     * un-deployable service.
     */
    public Mono<RecoveryReport> sweep(String trigger) {

        if (!this.enabled) return Mono.just(RecoveryReport.empty());

        return this.connectionService
                .allAppData(ConnectionSubType.MYSQL)
                .concatMap(conn -> this.mySQLAppDataService
                        .sweepInterrupted(conn, this.staleAfterSeconds)
                        .onErrorResume(e -> {
                            logger.error("Migration recovery failed for a MySQL app-data connection", e);
                            return Mono.just(RecoveryReport.empty());
                        }))
                .reduce(RecoveryReport.empty(), MigrationRecoveryService::merge)
                .doOnNext(report -> {
                    // Silence when there is nothing to say. A log line every five
                    // minutes saying "nothing happened" is how the line that matters
                    // gets missed.
                    if (report.isQuiet()) return;

                    if (report.unplayable().isEmpty()) logger.warn(
                            "Migration recovery ({}): {}", trigger, report.summary());
                    else logger.error("Migration recovery ({}): {}", trigger, report.summary());
                })
                .onErrorResume(e -> {
                    logger.error("Migration recovery sweep failed", e);
                    return Mono.just(RecoveryReport.empty());
                });
    }

    private static RecoveryReport merge(RecoveryReport a, RecoveryReport b) {

        java.util.Map<String, com.fincity.saas.commons.core.service.connection.appdata.mysql.MigrationOutcome>
                outcomes = new java.util.LinkedHashMap<>(a.outcomes());
        outcomes.putAll(b.outcomes());

        java.util.List<String> unplayable = new java.util.ArrayList<>(a.unplayable());
        unplayable.addAll(b.unplayable());

        return new RecoveryReport(
                a.found() + b.found(), a.claimed() + b.claimed(), a.resumed() + b.resumed(), outcomes, unplayable);
    }

    /** Exposed so a test, or an operator endpoint, can ask for a pass without waiting. */
    public Mono<RecoveryReport> sweepNow() {
        return this.sweep("manual");
    }

    static int defaultStaleSeconds() {
        return MySQLMigrationSweeper.DEFAULT_STALE_AFTER_SECONDS;
    }
}
