package com.fincity.security.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Registers a customer's domain with Cloudflare for SaaS, so the edge will
 * terminate TLS for a hostname we do not own.
 *
 * <h2>Why every method is best-effort</h2>
 *
 * None of these calls is allowed to fail a {@link ClientUrlService} operation.
 * A domain row that exists without a custom hostname is a domain that is not yet
 * served from the edge, which is exactly the state every domain was in before
 * this class existed; a custom hostname without a domain row is an orphan at
 * Cloudflare that costs nothing. Both are recoverable. Refusing to record the
 * customer's domain because a third party returned a 500 is not.
 *
 * The cost of that choice is that a failure here is invisible to the user, so
 * every one of them is logged at error with the hostname in it.
 *
 * <h2>Why nothing is stored</h2>
 *
 * Cloudflare's own id for a custom hostname is not kept anywhere. The hostname
 * is the natural key and the API can be queried by it, so a delete costs one
 * extra round trip and saves a column, a migration and a class of bug where the
 * stored id and the real one disagree.
 */
@Service
public class CloudflareCustomHostnameService {

    private static final Logger logger = LoggerFactory.getLogger(CloudflareCustomHostnameService.class);

    private static final String API_BASE = "https://api.cloudflare.com/client/v4/zones/";
    private static final String CUSTOM_HOSTNAMES = "/custom_hostnames";

    /**
     * Domain Control Validation by TXT record rather than HTTP.
     *
     * HTTP validation needs the hostname already resolving to Cloudflare, which
     * for a domain that is currently live somewhere else is a chicken and egg:
     * the certificate cannot be issued until DNS moves, and moving DNS before
     * there is a certificate shows every visitor a TLS error. TXT can be
     * satisfied while the domain still points at its old home, so the switch
     * happens with the certificate already active.
     */
    private static final String DCV_METHOD = "txt";

    @Value("${security.cloudflare.apiToken:}")
    private String apiToken;

    @Value("${security.cloudflare.zoneId:}")
    private String zoneId;

    /**
     * Suffixes we own, which must never be registered as custom hostnames.
     *
     * A custom hostname exists to let the edge serve a domain we do NOT own. Ours
     * are already covered by their zone's own certificate, so registering one is
     * redundant at best and a rejected call on every save at worst.
     *
     * Deliberately NOT read from {@code security.subdomain.endings}. That property
     * lists only {@code .modlix.com} and {@code .sitezump.ai}, because its job is
     * app subdomain resolution, and production carries LIVE rows on
     * {@code authzump.ai}, {@code adzump.ai}, {@code leadzump.ai} and
     * {@code fincity.com} that it does not cover. Reusing it would have sent four
     * of our own domains to Cloudflare as though they were a customer's.
     *
     * A bare entry matches the apex as well as anything under it.
     */
    @Value("${security.cloudflare.ownDomains:modlix.com,sitezump.ai,authzump.ai,adzump.ai,leadzump.ai,fincity.com}")
    private String[] ownDomains;

    private final WebClient webClient;

    /**
     * Takes the builder rather than calling {@code WebClient.create()} inline so a
     * test can supply an exchange function and assert on the request without a
     * socket. The bearer token is set per request instead of as a default header
     * because {@code apiToken} is field-injected and does not exist yet here.
     */
    public CloudflareCustomHostnameService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Whether this environment talks to Cloudflare at all.
     *
     * Local, dev and stage have no token, and must not: they share one zone with
     * production, so a custom hostname created from a developer's laptop would be
     * a live entry against real customer traffic. Absent configuration is the
     * normal case, not a misconfiguration, so it is never logged as one.
     */
    public boolean isConfigured() {
        return !StringUtil.safeIsBlank(this.apiToken) && !StringUtil.safeIsBlank(this.zoneId);
    }

    /**
     * Whether this hostname sits under a domain we own.
     *
     * Checked at this boundary rather than in the caller so it holds for every
     * caller, present and future. Matches the apex itself as well as anything
     * beneath it: {@code sitezump.ai} does not end with {@code .sitezump.ai}.
     */
    public boolean isOwnDomain(String hostname) {

        if (StringUtil.safeIsBlank(hostname) || this.ownDomains == null || this.ownDomains.length == 0)
            return false;

        String host = hostname.trim().toLowerCase();

        for (String each : this.ownDomains) {

            String apex = each.trim().toLowerCase();
            if (apex.startsWith("."))
                apex = apex.substring(1);
            if (apex.isEmpty())
                continue;

            if (host.equals(apex) || host.endsWith("." + apex))
                return true;
        }

        return false;
    }

    public Mono<Boolean> addHostname(String hostname) {

        if (!this.isConfigured() || StringUtil.safeIsBlank(hostname) || this.isOwnDomain(hostname))
            return Mono.just(Boolean.FALSE);

        return this.webClient
                .post()
                .uri(API_BASE + this.zoneId + CUSTOM_HOSTNAMES)
                .header(HttpHeaders.AUTHORIZATION, this.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "hostname", hostname,
                        "ssl", Map.of(
                                "method", DCV_METHOD,
                                "type", "dv",
                                "settings", Map.of("min_tls_version", "1.2"))))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (isSuccess(response)) {
                        logger.info("Cloudflare custom hostname created for {}", hostname);
                        return Boolean.TRUE;
                    }
                    logger.error("Cloudflare refused a custom hostname for {} : {}", hostname, errorsOf(response));
                    return Boolean.FALSE;
                })
                .onErrorResume(e -> {
                    logger.error("Cloudflare custom hostname call failed for {}", hostname, e);
                    return Mono.just(Boolean.FALSE);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CloudflareCustomHostnameService.addHostname"));
    }

    public Mono<Boolean> removeHostname(String hostname) {

        if (!this.isConfigured() || StringUtil.safeIsBlank(hostname) || this.isOwnDomain(hostname))
            return Mono.just(Boolean.FALSE);

        return this.findId(hostname)
                .flatMap(id -> this.webClient
                        .delete()
                        .uri(API_BASE + this.zoneId + CUSTOM_HOSTNAMES + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, this.bearer())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> {
                            if (isSuccess(response)) {
                                logger.info("Cloudflare custom hostname removed for {}", hostname);
                                return Boolean.TRUE;
                            }
                            logger.error("Cloudflare refused to remove {} : {}", hostname, errorsOf(response));
                            return Boolean.FALSE;
                        }))
                .defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(e -> {
                    logger.error("Cloudflare custom hostname removal failed for {}", hostname, e);
                    return Mono.just(Boolean.FALSE);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CloudflareCustomHostnameService.removeHostname"));
    }

    /**
     * Empty when the hostname is not registered, which is the ordinary case for
     * every row created before this class existed and for every environment that
     * was unconfigured at the time. Not finding one is not an error.
     */
    private Mono<String> findId(String hostname) {

        return this.webClient
                .get()
                .uri(API_BASE + this.zoneId + CUSTOM_HOSTNAMES + "?hostname=" + hostname)
                .header(HttpHeaders.AUTHORIZATION, this.bearer())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    if (!isSuccess(response))
                        return Mono.empty();

                    Object result = response.get("result");
                    if (!(result instanceof List<?> list) || list.isEmpty())
                        return Mono.empty();

                    Object first = list.getFirst();
                    if (!(first instanceof Map<?, ?> entry))
                        return Mono.empty();

                    Object id = entry.get("id");
                    return id == null ? Mono.empty() : Mono.just(id.toString());
                });
    }

    private String bearer() {
        return "Bearer " + this.apiToken;
    }

    private static boolean isSuccess(Map<?, ?> response) {
        return response != null && Boolean.TRUE.equals(response.get("success"));
    }

    /** The errors array as text, for a log line. Never the request, which carries the token. */
    private static String errorsOf(Map<?, ?> response) {
        Object errors = response == null ? null : response.get("errors");
        return errors == null ? "no detail" : errors.toString();
    }
}
