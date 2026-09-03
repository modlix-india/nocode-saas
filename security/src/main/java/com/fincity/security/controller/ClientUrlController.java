package com.fincity.security.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;
import com.fincity.security.model.DraftTokenResponse;
import com.fincity.security.service.ClientUrlService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/security/clienturls")
public class ClientUrlController
        extends AbstractJOOQDataController<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO, ClientUrlService> {

    @GetMapping("/fetchUrls")
    public Mono<List<String>> getUrlsOfApp(@RequestParam() String appCode, @RequestParam(required = false) String suffix) {
        return this.service.getUrlsBasedOnApp(appCode, suffix);
    }

    @GetMapping("/internal/applications/property/url")
    public Mono<ResponseEntity<String>> getAppUrl(@RequestParam String appCode,
                                                  @RequestParam(required = false) String clientCode) {
        return this.service.getAppUrl(appCode, clientCode).map(ResponseEntity::ok);
    }

    @GetMapping("/urls")
    public Mono<ResponseEntity<List<ClientUrl>>> getClientUrl(@RequestParam String appCode, @RequestParam String clientCode) {
        return this.service.getClientUrls(appCode, clientCode).map(ResponseEntity::ok);
    }

    /**
     * The app's draft hostname, or 404 when none has been minted.
     */
    @GetMapping("/draft")
    public Mono<ResponseEntity<ClientUrl>> getDraftUrl(@RequestParam String appCode) {
        return this.service.getDraftUrl(appCode)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Mint the app's draft hostname, or rotate it if one already exists. Rotating
     * revokes the previous link. Requires write access to the app, not just
     * Client_UPDATE.
     */
    @PostMapping("/draft")
    public Mono<ResponseEntity<ClientUrl>> mintDraftUrl(@RequestParam String appCode) {
        return this.service.mintDraftUrl(appCode).map(ResponseEntity::ok);
    }

    /**
     * The caller's grant of the draft surface for one app, as its own hostname.
     *
     * Gated like the draft URL above: write access to the app, not Client_UPDATE.
     * Unlike that one it neither rotates nor revokes: a caller already holding a live
     * grant for the app gets that same hostname back with its expiry pushed forward,
     * so a refresh, a second window and a restored tab all share one origin.
     */
    @PostMapping("/draft/token")
    public Mono<ResponseEntity<DraftTokenResponse>> mintDraftToken(@RequestParam String appCode) {
        return this.service.mintDraftToken(appCode).map(ResponseEntity::ok);
    }

    /**
     * Push an open editor's grant forward. Same token, same hostname, later expiry.
     */
    @PostMapping("/draft/token/extend")
    public Mono<ResponseEntity<DraftTokenResponse>> extendDraftToken(@RequestParam String token) {
        return this.service.extendDraftToken(token).map(ResponseEntity::ok);
    }

    /**
     * Whether a hostname's draft-edit token grants the draft surface for these
     * codes, and when that token expires.
     *
     * Called by the gateway before it stamps x-draft, so it is on the hot path for
     * every request out of a preview iframe and must never 404 or throw: an
     * unparseable, unknown, mismatched or expired token is a plain false and the
     * request is served live. Shaped after getClientNAppCodeNType for that reason.
     *
     * Internal, and under /clienturls/internal/ specifically because that prefix is
     * already permitted in SecurityConfiguration -- the gateway calls it with no
     * credentials at all.
     */
    @GetMapping("/internal/draft/token/resolve")
    public Mono<ResponseEntity<Tuple4<Boolean, String, String, String>>> resolveDraftToken(@RequestParam String host,
            @RequestParam(required = false, defaultValue = "") String appCode,
            @RequestParam(required = false, defaultValue = "") String clientCode) {

        return this.service.resolveDraftToken(host, appCode, clientCode)
                .defaultIfEmpty(Tuples.of(Boolean.FALSE, "0", "", ""))
                .map(ResponseEntity::ok);
    }
}
