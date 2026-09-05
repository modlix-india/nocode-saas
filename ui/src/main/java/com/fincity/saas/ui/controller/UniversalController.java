package com.fincity.saas.ui.controller;

import java.net.URI;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.feign.IFeignSecurityBillingService;
import com.fincity.saas.ui.model.billing.HostingDecision;
import com.fincity.saas.ui.service.IndexHTMLService;
import com.fincity.saas.ui.service.JSService;
import com.fincity.saas.ui.service.ManifestService;
import com.fincity.saas.ui.service.URIPathService;
import com.fincity.saas.ui.utils.ResponseEntityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;

@RestController
public class UniversalController {

    private final JSService jsService;

    private final IndexHTMLService indexHTMLService;

    private final ManifestService manifestService;

    private final URIPathService uriPathService;

    private final IFeignSecurityService securityService;

    private final IFeignSecurityBillingService billingSecurityService;

    private final Gson gson;

    @Value("${ui.resourceCacheAge:604800}")
    private int cacheAge;

    private static final ResponseEntity<String> RESPONSE_NOT_FOUND = ResponseEntity
            .notFound()
            .build();

    private static final ResponseEntity<String> RESPONSE_BAD_REQUEST = ResponseEntity
            .badRequest()
            .build();

    private final static String START = "<html><head><title>SSO</title><script>";
    private final static String END = "</script></head><body></body></html>";

    public UniversalController(JSService jsService, IndexHTMLService indexHTMLService, ManifestService manifestService,
            URIPathService uriPathService, IFeignSecurityService securityService,
            IFeignSecurityBillingService billingSecurityService, Gson gson) {
        this.jsService = jsService;
        this.indexHTMLService = indexHTMLService;
        this.manifestService = manifestService;
        this.uriPathService = uriPathService;
        this.securityService = securityService;
        this.billingSecurityService = billingSecurityService;
        this.gson = gson;
    }

    @GetMapping(value = "js/dist/**")
    public Mono<ResponseEntity<String>> indexJS(@RequestHeader(name = "If-None-Match", required = false) String eTag,
            ServerHttpRequest request) {

        int index = request.getURI().getPath().indexOf("/js/dist/");
        String filePath = request.getURI().getPath().substring(index + 9);

        return jsService.getJSResource(filePath)
                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                .defaultIfEmpty(RESPONSE_NOT_FOUND);
    }

    @GetMapping(value = "manifest/manifest.json", produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> manifest(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader(name = "If-None-Match", required = false) String eTag) {

        return manifestService.getManifest(appCode, clientCode)
                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                .defaultIfEmpty(RESPONSE_NOT_FOUND);
    }

    @GetMapping(value = "/apiDocs", produces = MimeTypeUtils.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> apiDocs(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader(name = "If-None-Match", required = false) String eTag) {

        return uriPathService.generateApiDocs(appCode, clientCode)
                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                .defaultIfEmpty(RESPONSE_NOT_FOUND);
    }

    @GetMapping(value = "**")
    public Mono<ResponseEntity<String>> defaultGetRequest(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader(name = "If-None-Match", required = false) String eTag,
            ServerHttpRequest request) {

        // Hosting gate: serve the suspend app/client when M's builder wallet is
        // suspended. Best-effort — any error or empty result serves the requested app.
        var pageMono = Mono
                .defer(() -> this.billingSecurityService.checkHosting(appCode, clientCode)
                        .onErrorReturn(new HostingDecision(false, appCode, clientCode))
                        .defaultIfEmpty(new HostingDecision(false, appCode, clientCode))
                        .flatMap(d -> indexHTMLService.getIndexHTML(d.serveAppCode(), d.serveClientCode()))
                        .flatMap(e -> ResponseEntityUtils
                                .makeResponseEntity(e, eTag, cacheAge, MimeTypeUtils.TEXT_HTML_VALUE)));

        if (!request.getPath().toString().contains("/api/"))
            return pageMono;

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca.isAuthenticated() ? Mono.just(ca.getClientCode()) : Mono.just(clientCode),

                (ca, cc) -> uriPathService.getResponse(request, null, appCode, cc, forwardedHost, forwardedPort)
                        .map(ResponseEntity::ok))
                .switchIfEmpty(pageMono);
    }

    @RequestMapping(value = "**", produces = MimeTypeUtils.APPLICATION_JSON_VALUE, method = { RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE })
    public Mono<ResponseEntity<String>> defaultRequests(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader(name = "If-None-Match", required = false) String eTag,
            ServerHttpRequest request,
            @RequestBody String jsonString) {

        JsonObject jsonObject = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
                : this.gson.fromJson(jsonString, JsonObject.class);

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca.isAuthenticated() ? Mono.just(ca.getClientCode()) : Mono.just(clientCode),

                (ca, cc) -> uriPathService.getResponse(request, jsonObject, appCode, cc, forwardedHost, forwardedPort)
                        .map(ResponseEntity::ok))
                .switchIfEmpty(Mono.just(RESPONSE_BAD_REQUEST));
    }

    @GetMapping("/.well-known/acme-challenge/{token}")
    public Mono<ResponseEntity<String>> tokenCheck(@PathVariable String token) {

        return this.securityService.token(token).map(ResponseEntity::ok);
    }

    /**
     * The cross-app SSO beacon, always a TOP-LEVEL navigation.
     *
     * There used to be an iframe/postMessage mode here as well. It could never work across
     * registrable domains: storage reached from a third-party context is partitioned by
     * top-level site, so the session this origin really holds is invisible from inside
     * another app's page in every current browser. It has been removed rather than left as a
     * path that silently returns "no session" to everyone.
     */
    @GetMapping(value = "/hassso", produces = MimeTypeUtils.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> hassso(
            @RequestParam String targetAppCode,
            @RequestParam(required = false, defaultValue = "") String targetClientCode,
            @RequestParam(required = false, defaultValue = "") String returnUrl) {

        return this.hasssoBounce(targetAppCode, targetClientCode, returnUrl);
    }

    private static ResponseEntity<String> beaconResponse(String htmlContent) {
        return ResponseEntity.ok()
                .header("Content-Security-Policy", "frame-ancestors *")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(htmlContent);
    }

    /**
     * The cold-start half of cross-domain SSO, as a TOP-LEVEL navigation instead of an
     * iframe.
     * <p>
     * The iframe path above cannot work across registrable domains: storage reached from a
     * third-party context is partitioned by top-level site, so what one app writes here is
     * invisible to the next. Loaded top-level, this page reads the beacon origin's own
     * first-party storage, mints a one-time token and hands it back on the URL. No
     * third-party cookie, no Storage Access API, no prompt, and it works in every browser.
     * <p>
     * {@code returnUrl} is attacker-controlled and the token is a session, so an open
     * redirect here is an account takeover. It is checked against the platform's own record:
     * the host must resolve to the very app the token is being minted for. An unknown host
     * resolves to appCode "nothing" and is refused.
     */
    private Mono<ResponseEntity<String>> hasssoBounce(String targetAppCode, String targetClientCode,
            String returnUrl) {

        URI uri = absoluteHttpUri(returnUrl);

        if (uri == null || StringUtil.safeIsBlank(targetAppCode))
            return Mono.just(RESPONSE_BAD_REQUEST);

        return this.securityService
                .getClientNAppCodeNType(uri.getScheme(), uri.getHost(),
                        uri.getPort() < 0 ? "" : String.valueOf(uri.getPort()))
                .filter(t -> targetAppCode.equals(t.getT2()))
                .map(t -> beaconResponse(START + bounceScript(returnUrl, targetAppCode, targetClientCode) + END))
                .defaultIfEmpty(RESPONSE_NOT_FOUND);
    }

    /**
     * Reads the beacon origin's first-party token, then leaves, one way or the other. The
     * caller is told "no session" explicitly rather than being left to time out, because it
     * has to record that it asked and stop asking on every page load.
     */
    private static String bounceScript(String returnUrl, String targetAppCode, String targetClientCode) {

        return "var back=" + jsString(returnUrl) + ";" +
                "var targetAppCode=" + jsString(targetAppCode) + ";" +
                "var targetClientCode=" + jsString(targetClientCode) + ";" +
                "function leave(k,v){var u=new URL(back);u.searchParams.set(k,v);window.location.replace(u.toString());}" +
                "var lsToken=localStorage.getItem('AuthToken');" +
                "var lsExpiry=parseInt(localStorage.getItem('AuthTokenExpiry')||'0',10)*1000;" +
                "if(!lsToken||lsExpiry<Date.now()){leave('sso','none');}" +
                "else{" +
                "var bearer;try{bearer=JSON.parse(lsToken);}catch(e){bearer=lsToken;}" +
                "fetch('/api/security/makeOneTimeToken',{method:'POST',headers:{'Content-Type':'application/json',"
                + "'Authorization':bearer,'appCode':'authzump','clientCode':'SYSTEM'},"
                + "body:JSON.stringify({targetAppCode:targetAppCode,targetClientCode:targetClientCode})})" +
                ".then(function(r){return r.ok?r.json():null;})" +
                ".then(function(d){if(d&&d.token){leave('ott',d.token);}else{leave('sso','none');}})" +
                ".catch(function(){leave('sso','none');});" +
                "}";
    }

    /** An absolute http(s) URI, or null. Anything else is not a place to send a session. */
    // Package-private so HasssoBounceTest can exercise the return-URL check directly.
    static URI absoluteHttpUri(String value) {

        if (StringUtil.safeIsBlank(value))
            return null;

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
            return null;

        return StringUtil.safeIsBlank(uri.getHost()) ? null : uri;
    }

    // Package-private so SsoRedirectGuardTest can exercise the escaping directly.
    static String jsString(String s) {
        if (s == null)
            return "''";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('\'');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\'' -> sb.append("\\'");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                case 0x2028 -> sb.append("\\u2028");
                case 0x2029 -> sb.append("\\u2029");
                default -> sb.append(c);
            }
        }
        sb.append('\'');
        return sb.toString();
    }

    @GetMapping(value = "/sso/{token}", produces = MimeTypeUtils.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> ssoRedirection(@PathVariable String token,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost,
            @RequestHeader(required = false) String clientCode,
            @RequestHeader(required = false) String appCode,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress,
            @RequestParam(required = false, defaultValue = "/") String redirectUrl,
            ServerHttpRequest request) {

        final String addr = ipAddress != null ? ipAddress
                : (request.getRemoteAddress() == null ? ""
                        : request.getRemoteAddress().getAddress().getHostAddress());

        return this.resolveSsoRedirect(redirectUrl, request).flatMap(safeRedirect -> {

            String redirectionScript = "window.location.href = " + jsString(safeRedirect) + ";";

            return this.securityService
                    .authenticateWithOneTimeToken(token, forwardedHost, clientCode, appCode, addr)
                    .map(ca -> {
                        // The token is stored JSON-encoded because the beacon reads it back
                        // with JSON.parse; jsString then makes the whole value a safe literal.
                        String storeTokenScript = "window.localStorage.setItem('AuthToken', "
                                + jsString("\"" + ca.getAccessToken() + "\"")
                                + ");window.localStorage.setItem('AuthTokenExpiry', '"
                                + ca.getAccessTokenExpiryAt().toEpochSecond(ZoneOffset.UTC)
                                + "');";

                        return ResponseEntity.ok()
                                .header("Content-Security-Policy", "frame-ancestors *")
                                .body(START + storeTokenScript + redirectionScript + END);
                    });
        });
    }

    /**
     * Guards the post-SSO redirect against open-redirect abuse. {@link #jsString(String)} stops
     * the value breaking out of the inline script; this stops it pointing somewhere else.
     * <p>
     * Same-origin is not enough. The beacon seed deliberately lands here on one host and
     * continues to an app on another, which is the whole point of cross-domain SSO, so a
     * strict same-origin rule sent every seeded login to "/" on the beacon instead of back to
     * the app. A cross-origin destination is therefore allowed when the platform recognises
     * its host as belonging to a real app; an unknown host resolves to appCode
     * {@value #UNKNOWN_HOST_APP_CODE} and is refused.
     * <p>
     * Note the asymmetry with {@link #hasssoBounce}. There, the server mints a token from the
     * visitor's own session, so the destination is pinned to the one app the token is for.
     * Here the token arrives in the URL from the caller, who could only ever supply their own,
     * so the weaker rule costs nothing and the stricter one breaks the feature.
     * <p>
     * Anything rejected falls back to "/" rather than failing the login.
     */
    private Mono<String> resolveSsoRedirect(String redirectUrl, ServerHttpRequest request) {

        String decided = decideRedirectWithoutLookup(redirectUrl, clientFacingHost(request));
        if (decided != null)
            return Mono.just(decided);

        URI uri = absoluteHttpUri(redirectUrl);

        return this.securityService
                .getClientNAppCodeNType(uri.getScheme(), uri.getHost(),
                        uri.getPort() < 0 ? "" : String.valueOf(uri.getPort()))
                .map(t -> isRealApp(t.getT2()) ? redirectUrl : "/")
                .defaultIfEmpty("/");
    }

    /**
     * The half of the decision that needs no lookup. Returns the destination to use, or null
     * when only a host lookup can settle it. Package-private so the rules that do not depend
     * on the platform's data stay directly testable.
     */
    static String decideRedirectWithoutLookup(String redirectUrl, String ownHost) {

        if (redirectUrl == null || redirectUrl.isBlank())
            return "/";

        // A single leading slash is a same-origin path. "//host" and "/\host" are
        // protocol-relative URLs that browsers resolve against another origin.
        if (redirectUrl.charAt(0) == '/')
            return redirectUrl.length() > 1
                    && (redirectUrl.charAt(1) == '/' || redirectUrl.charAt(1) == '\\')
                            ? "/"
                            : redirectUrl;

        URI uri = absoluteHttpUri(redirectUrl);
        if (uri == null)
            return "/";

        // getHost() resolves "https://trusted.example@evil.example/" to evil.example, so a
        // userinfo prefix cannot spoof the comparison.
        if (uri.getHost().equalsIgnoreCase(ownHost))
            return redirectUrl;

        return null;
    }

    /** The host resolver answers with this appCode for a host it has no ClientUrl row for. */
    private static final String UNKNOWN_HOST_APP_CODE = "nothing";

    private static boolean isRealApp(String appCode) {
        return !StringUtil.safeIsBlank(appCode) && !UNKNOWN_HOST_APP_CODE.equals(appCode);
    }

    static String clientFacingHost(ServerHttpRequest request) {

        String raw = request.getHeaders().getFirst("X-Forwarded-Host");

        if (raw == null || raw.isBlank())
            return request.getURI().getHost();

        int comma = raw.indexOf(',');
        String first = (comma >= 0 ? raw.substring(0, comma) : raw).trim();
        int colon = first.indexOf(':');
        return colon >= 0 ? first.substring(0, colon) : first;
    }
}
