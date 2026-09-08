package com.fincity.saas.message.service.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks instances that have stopped heartbeating.
 *
 * <p>Without this, a dead instance stays {@code UP} forever and keeps being handed new sessions,
 * every one of which fails to link because nothing is there to hold it. Liveness has to be decided
 * by something that runs whether or not the instance calls in, which is precisely what a heartbeat
 * cannot do for itself.
 *
 * <p>Every fifteen seconds, matching the heartbeat interval, so an instance is marked down within
 * one sweep of crossing the timeout rather than up to a minute later.
 */
@Component
public class BridgeHealthSweeper {

    private static final Logger logger = LoggerFactory.getLogger(BridgeHealthSweeper.class);

    private final BridgeRegistryService registryService;

    public BridgeHealthSweeper(BridgeRegistryService registryService) {
        this.registryService = registryService;
    }

    @Scheduled(
            initialDelayString = "${message.bridge.health-sweep-initial-delay-ms:30000}",
            fixedDelayString = "${message.bridge.health-sweep-interval-ms:15000}")
    public void sweep() {
        this.registryService
                .sweepStaleInstances()
                .subscribe(null, e -> logger.error("Bridge health sweep failed.", e));
    }
}
