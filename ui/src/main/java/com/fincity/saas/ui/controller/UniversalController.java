package com.fincity.saas.ui.controller;

import java.time.Duration;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

    @GetMapping(value = "/hassso", produces = MimeTypeUtils.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> hassso(
            @RequestParam String parentURL,
            @RequestParam String targetAppCode,
            @RequestParam(required = false, defaultValue = "") String targetClientCode,
            @RequestParam(required = false) Boolean designMode) {

        String safeParentURL = jsString(parentURL);
        String safeTargetAppCode = jsString(targetAppCode);
        String safeTargetClientCode = jsString(targetClientCode);
        // The parent passes designMode explicitly because /hassso always runs in an iframe.
        String designModeJs = designMode == null
                ? "var designMode=window.self!==window.top;"
                : "var designMode=" + designMode + ";";

        String script =
                "var parentURL=" + safeParentURL + ";" +
                "var targetAppCode=" + safeTargetAppCode + ";" +
                "var targetClientCode=" + safeTargetClientCode + ";" +
                "var parentOrigin=new URL(parentURL).origin;" +
                designModeJs +
                "var tokenKey=(designMode?'designMode_':'')+'AuthToken';" +
                "var expiryKey=(designMode?'designMode_':'')+'AuthTokenExpiry';" +
                "function postNone(){window.parent.postMessage({type:'sso:none'},parentOrigin);}" +
                "function postToken(t){window.parent.postMessage({type:'sso:token',token:t},parentOrigin);}" +
                "var lsToken=localStorage.getItem(tokenKey);" +
                "var lsExpiry=parseInt(localStorage.getItem(expiryKey)||'0',10)*1000;" +
                "if(!lsToken||lsExpiry<Date.now()){postNone();}" +
                "else{" +
                "var bearer;try{bearer=JSON.parse(lsToken);}catch(e){bearer=lsToken;}" +
                "fetch('/api/security/makeOneTimeToken',{method:'POST',headers:{'Content-Type':'application/json','Authorization':bearer,'appCode':'authzump','clientCode':'SYSTEM'},body:JSON.stringify({targetAppCode:targetAppCode,targetClientCode:targetClientCode})})" +
                ".then(function(r){return r.ok?r.json():null;})" +
                ".then(function(d){if(d&&d.token){postToken(d.token);}else{postNone();}})" +
                ".catch(function(){postNone();});" +
                "}";

        String htmlContent = START + script + END;

        return Mono.just(ResponseEntity.ok()
                .header("Content-Security-Policy", "frame-ancestors *")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(htmlContent));
    }

    private static String jsString(String s) {
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
            @RequestParam(defaultValue = "false") boolean cookie,
            @RequestParam(required = false) Boolean designMode,
            ServerHttpRequest request) {

        String addr = ipAddress;
        if (addr == null) {
            addr = request.getRemoteAddress() == null ? "" : request.getRemoteAddress().getAddress().getHostAddress();
        }

        // The caller passes designMode when /sso/{token} runs inside a hidden iframe
        // (where window.self !== window.top would always be true). For top-level legacy
        // callers (magic link, password reset) we fall back to the existing detection.
        String designModeJs = designMode == null
                ? "var designMode = window.self !== window.top;"
                : "var designMode = " + designMode + ";";

        return this.securityService.authenticateWithOneTimeToken(token, forwardedHost, clientCode, appCode, addr)
                .map(ca -> {
                    String storeTokenScript = designModeJs +
                            "window.localStorage.setItem((designMode ? 'designMode_' : '')+'AuthToken', '\""
                            + ca.getAccessToken()
                            + "\"');window.localStorage.setItem((designMode ? 'designMode_' : '')+'AuthTokenExpiry', '"
                            + ca.getAccessTokenExpiryAt().toEpochSecond(ZoneOffset.UTC) + "');";
                    String redirectionScript = "window.location.href = '" + redirectUrl + "';";
                    String htmlContent = START + storeTokenScript + redirectionScript + END;
                    ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                            .header("Content-Security-Policy", "frame-ancestors *");

                    if (cookie) {
                        ResponseCookie responseCookie = ResponseCookie
                                .from("AuthToken", ca.getAccessToken())
                                .path("/")
                                .maxAge(Duration.ofSeconds(
                                        ca.getAccessTokenExpiryAt().toEpochSecond(ZoneOffset.UTC)))
                                .build();
                        responseBuilder.header(HttpHeaders.SET_COOKIE, responseCookie.toString());
                    }

                    return responseBuilder.body(htmlContent);
                });
    }
}
