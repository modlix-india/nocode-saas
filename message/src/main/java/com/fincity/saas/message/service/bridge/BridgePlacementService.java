package com.fincity.saas.message.service.bridge;

import com.fincity.saas.message.dao.bridge.BridgeInstanceDAO;
import com.fincity.saas.message.dto.bridge.BridgeInstance;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Chooses which instance a new session is created on.
 *
 * <p>Placement happens once, at create, and the answer is then permanent. Everything after it is a
 * table lookup rather than a routing decision: no hashing, because sessions must not move when the
 * fleet changes, and no failover, because a session has exactly one home and a second home means two
 * processes on one device store.
 */
@Service
public class BridgePlacementService {

    private static final Logger logger = LoggerFactory.getLogger(BridgePlacementService.class);

    private final BridgeInstanceDAO instanceDao;
    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    public BridgePlacementService(BridgeInstanceDAO instanceDao) {
        this.instanceDao = instanceDao;
    }

    /**
     * The ISO country a number belongs to.
     *
     * <p>libphonenumber rather than a dial-code prefix table, and the difference is not academic:
     * {@code +44 7911 ...} is Guernsey, not the United Kingdom, because Guernsey shares +44 and owns
     * that range. A prefix table gets that wrong, places the session on the wrong country's
     * instance, and produces a COUNTRY_MISMATCH at pair time that looks like a customer error.
     *
     * <p>Advisory only. The number here is whatever the caller declared, and a QR code can be
     * scanned by any handset in the room, so the authoritative check is the bridge's own at
     * PairSuccess against the linked JID. This exists to pick a plausible instance and to fail early
     * with a comprehensible message rather than after somebody has picked up a phone.
     */
    public Optional<String> countryOf(String phoneNumber) {

        if (phoneNumber == null || phoneNumber.isBlank()) return Optional.empty();

        String e164 = phoneNumber.startsWith("+") ? phoneNumber : "+" + phoneNumber.trim();

        try {
            Phonenumber.PhoneNumber parsed = this.phoneNumberUtil.parse(e164, null);
            String region = this.phoneNumberUtil.getRegionCodeForNumber(parsed);

            // Deliberately unassigned ranges parse fine and resolve to no region at all. Treated as
            // unplaceable rather than defaulted to anything.
            if (region == null || region.isBlank() || "ZZ".equals(region)) return Optional.empty();

            return Optional.of(region.toUpperCase(Locale.ROOT));
        } catch (NumberParseException e) {
            logger.warn("Could not determine the country for {}: {}", phoneNumber, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Picks an instance for a number, or fails loudly.
     *
     * <p>Never silently overfills and never falls back to another country's instance. A session
     * placed on a box in the wrong country connects from the wrong IP, which is described everywhere
     * as a first-order signal for exactly the enforcement this design exists to avoid, and it would
     * be the customer's real number that paid for it.
     */
    public Mono<BridgeInstance> place(String phoneNumber) {

        Optional<String> country = this.countryOf(phoneNumber);

        if (country.isEmpty())
            return Mono.error(new IllegalArgumentException(
                    "Could not determine which country " + phoneNumber + " belongs to, so it cannot be placed."));

        return this.place(country.get(), phoneNumber);
    }

    public Mono<BridgeInstance> place(String country, String phoneNumber) {

        return this.instanceDao.listPlaceable().flatMap(candidates -> {
            List<BridgeInstance> serving =
                    candidates.stream().filter(i -> i.serves(country)).toList();

            if (serving.isEmpty()) {
                logger.error(
                        "No bridge instance is registered for country {} (wanted for {}).",
                        country,
                        phoneNumber);
                // Phrased for a person rather than for a log. This surfaces to the customer through
                // entity-processor, and "not available in this country yet" is true, actionable and
                // not alarming, where a connection error is none of those.
                return Mono.error(new IllegalStateException(
                        "WhatsApp is not available in " + country + " yet."));
            }

            // Already ordered by active session count, so this is the emptiest instance serving the
            // country. Spreading rather than packing is deliberate: numbers concentrated behind one
            // address is itself the pattern that draws attention, and one restart taking a full
            // instance down produces a simultaneous reconnect storm.
            Optional<BridgeInstance> chosen =
                    serving.stream().filter(BridgeInstance::hasRoom).findFirst();

            if (chosen.isEmpty()) {
                logger.error(
                        "Every bridge instance serving {} is at its session cap. Provision another"
                                + " before the next signup, rather than raising the cap: it is a risk"
                                + " limit, not a capacity limit.",
                        country);
                return Mono.error(new IllegalStateException(
                        "No capacity to link another WhatsApp number in " + country + " right now."));
            }

            logger.info(
                    "Placing {} on bridge {} ({} of {} slots used).",
                    phoneNumber,
                    chosen.get().getInstanceId(),
                    chosen.get().getActiveSessions(),
                    chosen.get().getSessionCap());

            return Mono.just(chosen.get());
        });
    }
}
