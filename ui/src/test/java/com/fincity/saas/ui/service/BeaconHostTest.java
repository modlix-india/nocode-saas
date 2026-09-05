package com.fincity.saas.ui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the SSO beacon host per environment. Getting local wrong points the beacon iframe at
 * a host that 404s and breaks all local SSO silently, which is exactly what happened before.
 * <p>
 * htmlRenderer.ts in nocode-ui/ui-app/ssr carries a copy of this mapping and must agree.
 */
class BeaconHostTest {

    @Test
    void deployedEnvironmentsLiveOnTheAiDomain() {

        assertEquals("authzump.ai", IndexHTMLService.deriveBeaconHost(""));
        assertEquals("authzump.ai", IndexHTMLService.deriveBeaconHost(null));
        assertEquals("dev.authzump.ai", IndexHTMLService.deriveBeaconHost(".dev"));
        assertEquals("stage.authzump.ai", IndexHTMLService.deriveBeaconHost(".stage"));
        // A leading dot is the convention but not required.
        assertEquals("dev.authzump.ai", IndexHTMLService.deriveBeaconHost("dev"));
    }

    @Test
    void localLivesOnModlixComBecauseThatIsWhatDnsmasqWildcards() {

        assertEquals("authzump.local.modlix.com", IndexHTMLService.deriveBeaconHost(".local"));
        assertEquals("authzump.local.modlix.com", IndexHTMLService.deriveBeaconHost("local"));
    }

    @Test
    void onlyTheFirstSuffixSegmentNamesTheEnvironment() {

        assertEquals("dev.authzump.ai", IndexHTMLService.deriveBeaconHost(".dev.something"));
        assertEquals("authzump.local.modlix.com", IndexHTMLService.deriveBeaconHost(".local.something"));
    }
}
