package com.fincity.security.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemReader;
import org.jooq.types.ULong;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.Problem;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.SSLCertificateDAO;
import com.fincity.security.dao.SSLChallengeDAO;
import com.fincity.security.dao.SSLRequestDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.dto.SSLCertificate;
import com.fincity.security.dto.SSLChallenge;
import com.fincity.security.dto.SSLRequest;
import com.fincity.security.model.SSLCertificateConfiguration;
import com.fincity.security.model.SSLCertificateOrder;
import com.fincity.security.model.SSLCertificateOrderRequest;
import com.fincity.security.model.SSLCertificateRenewalResult;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class SSLCertificateService {

    private static final Logger logger = LoggerFactory.getLogger(SSLCertificateService.class);

    public static final String CACHE_NAME_CERTIFICATE = "certificateCache";

    private static final String CACHE_CERTIFICATE_VALUE = "certificates";

    public static final String CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT = "certificatesLastUpdatedCache";

    private static final String CACHE_CERTIFICATE_LAST_UPDATED_VALUE = "certificatesLastUpdated";

    private final SecurityMessageResourceService msgService;

    private final SSLCertificateDAO certificateDao;

    private final SSLRequestDAO requestDao;

    private final SSLChallengeDAO challengeDao;

    private final ClientUrlService clientUrlService;

    private final CacheService cacheService;

    @Value("${letsencrypt.session:}")
    private String sessionURL;

    @Value("${letsencrypt.login:}")
    private String accountURL;

    @Value("${letsencrypt.key:}")
    private String accountKey;

    private KeyPair accountKeyPair;

    public SSLCertificateService(SecurityMessageResourceService msgService, SSLCertificateDAO certificateDao,
                                 SSLRequestDAO requestDao, SSLChallengeDAO challengeDao, ClientUrlService clientUrlService,
                                 CacheService cacheService) {
        this.msgService = msgService;
        this.certificateDao = certificateDao;
        this.requestDao = requestDao;
        this.challengeDao = challengeDao;
        this.clientUrlService = clientUrlService;
        this.cacheService = cacheService;
    }

    @PostConstruct
    public void initialize() {

        try {
            this.accountKeyPair = KeyPairUtils.readKeyPair(new StringReader(this.accountKey));
        } catch (IOException ex) {
            logger.debug("Exception while parsing the account keypair.", ex);
        }
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Page<SSLCertificate>> findSSLCertificates(ULong urlId, Pageable pageable, AbstractCondition condition) {

        return FlatMapUtil.flatMapMono(

            () -> this.clientUrlService.read(urlId),

            clientUrl -> Mono.just(condition == null || condition.isEmpty() ? FilterCondition.make("urlId", urlId)
                : ComplexCondition.and(FilterCondition.make("urlId", urlId), condition)),

            (clientUrl, cond) -> this.certificateDao.readPageFilter(pageable, cond));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<SSLCertificate> createExternallyIssuedCertificate(SSLCertificate certificate) {

        return FlatMapUtil.flatMapMono(

                () -> this.validateCrtAndKey(certificate),

                valid -> this.clientUrlService.read(certificate.getUrlId()),

                (valid, clientUrl) -> this.certificateDao.create(certificate)

            )
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createCertificate"))
            .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_CERTIFICATE))
            .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> validateCrtAndKey(SSLCertificate certificate) {

        if (StringUtil.safeIsBlank(certificate.getCrtKey())) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                SecurityMessageResourceService.CRT_KEY_ISSUE, "Key is missing");
        }

        if (StringUtil.safeIsBlank(certificate.getCrt())) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                SecurityMessageResourceService.CRT_KEY_ISSUE, "Certificate is missing");
        }

        X509Certificate cert;
        try {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate.getCrt().getBytes()));
            Date notAfter = cert.getNotAfter();
            Date now = new Date();
            if (notAfter.before(now))
                return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.CRT_KEY_ISSUE, "Certificate is expired");

        } catch (CertificateException ex) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, ex),
                SecurityMessageResourceService.CRT_KEY_ISSUE, "Error while reading the certificate");
        }

        PublicKey publicKey = cert.getPublicKey();

        PrivateKey privateKey;

        try {
            privateKey = this.parsePrivateKey(certificate.getCrtKey());
        } catch (IOException ex) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, ex),
                SecurityMessageResourceService.CRT_KEY_ISSUE, "Error while reading the key");
        }

        try {

            Cipher iesCipher = Cipher.getInstance("RSA"); // NOSONAR
            iesCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] ciphertext = iesCipher.doFinal("TEST my string for encryption".getBytes());
            iesCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plaintext = iesCipher.doFinal(ciphertext);

            String decryptedString = new String(plaintext);

            if (!decryptedString.equals("TEST my string for encryption")) {
                return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.CRT_KEY_ISSUE, "Error while matching the certificate and key");
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException
                 | NoSuchPaddingException ex) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, ex),
                SecurityMessageResourceService.CRT_KEY_ISSUE,
                "Error while matching the certificate and key. Either the certificate or key is incorrect, or Certificate and key are not based on RSA algorithm.");
        }
        return Mono.just(true);
    }

    private PrivateKey parsePrivateKey(String key)
        throws IOException {
        try (PEMParser pemParser = new PEMParser(new StringReader(key))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            Object o = pemParser.readObject();
            if (o instanceof PEMKeyPair pemKeyPair)
                return converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());

            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(o);
            return converter.getPrivateKey(privateKeyInfo);
        }
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Boolean> createCertificate(ULong requestId) {
        return this.createCertificateInternal(requestId);
    }

    private Mono<Boolean> createCertificateInternal(ULong requestId) {

        return FlatMapUtil.flatMapMono(

                () -> this.requestDao.readById(requestId),

                this::loginAndBindOrder,

                (request, order) -> {

                    try {

                        order.update();

                        PemReader reader = new PemReader(new StringReader(request.getCsr()));
                        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(reader.readPemObject()
                            .getContent());

                        order.execute(csr.getEncoded());

                        int attempts = 10;
                        while (order.getStatus() != Status.VALID && attempts-- > 0) {

                            if (order.getStatus() == Status.INVALID) {
                                String diagnostics = this.buildOrderDiagnostics(order);
                                logger.error("Order is INVALID for domains [{}] — {}", request.getDomains(), diagnostics);
                                return this.msgService.throwMessage(
                                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                    SecurityMessageResourceService.LETS_ENCRYPT_ISSUE, diagnostics);
                            }

                            // Because of network issue we will retry automatically.
                            Thread.sleep(3000L); // NO SONAR

                            order.update();
                        }

                        if (order.getStatus() != Status.VALID) {
                            String diagnostics = this.buildOrderDiagnostics(order);
                            logger.error("Order timed out with status {} for domains [{}] — {}", order.getStatus(), request.getDomains(), diagnostics);
                            return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                SecurityMessageResourceService.LETS_ENCRYPT_ISSUE, diagnostics);
                        }
                    } catch (InterruptedException ex) {

                        Thread.currentThread()
                            .interrupt();
                    } catch (AcmeException | IOException ex) {
                        return this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
                            SecurityMessageResourceService.LETS_ENCRYPT_ISSUE, ex.getMessage());
                    }

                    return Mono.just(order.getCertificate());
                },

                (request, order, certificate) -> this.certificateDao.create(request, certificate)

            )
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createCertificateInternal"))
            .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_CERTIFICATE))
            .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT))
            .subscribeOn(Schedulers.boundedElastic())
            .map(e -> true);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<SSLCertificateOrder> triggerChallenge(ULong challengeId) {

        return FlatMapUtil.flatMapMono(

                () -> this.challengeDao.readById(challengeId),

                challenge -> this.requestDao.readById(challenge.getRequestId()),

                this::triggerChallenge,

                (challenge, request, triggered) -> this.readRequestByURLId(request.getUrlId()))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.triggerChallenge"))
            .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT))
            .subscribeOn(Schedulers.boundedElastic());

    }

    private Mono<Boolean> triggerChallenge(SSLChallenge challenge, SSLRequest request) {

        return FlatMapUtil.flatMapMono(

                () -> this.loginAndBindOrder(request),

                order -> {

                    var authChallengeTup = this.findChallengeAndAuthorization(order, challenge);

                    if (authChallengeTup == null) {
                        return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                            AbstractMessageService.OBJECT_NOT_FOUND);
                    }

                    Authorization auth = authChallengeTup.getT1();
                    Challenge ch = authChallengeTup.getT2();

                    Tuple2<String, String> newTokens = this.resolveTokenFromChallenge(auth, ch, challenge);

                    boolean tokensChanged = !newTokens.getT1().equals(challenge.getToken())
                        || !newTokens.getT2().equals(challenge.getAuthorization());

                    if (tokensChanged) {
                        logger.info("Challenge tokens changed for domain: {}, updating DB before triggering",
                            challenge.getDomain());
                    }

                    return (tokensChanged
                            ? this.challengeDao.updateTokenAndAuthorization(challenge.getId(), newTokens.getT1(), newTokens.getT2())
                            : Mono.just(true))
                        .flatMap(updated -> this.triggerAndUpdateStatus(auth, ch, order, challenge));
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.triggerChallenge"))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Tuple2<String, String> resolveTokenFromChallenge(Authorization auth, Challenge ch, SSLChallenge stored) {

        if (ch instanceof Http01Challenge hch)
            return Tuples.of(hch.getToken(), hch.getAuthorization());

        if (ch instanceof Dns01Challenge dch)
            return Tuples.of(Dns01Challenge.toRRName(auth.getIdentifier().getDomain()), dch.getDigest());

        return Tuples.of(stored.getToken(), stored.getAuthorization());
    }

    private Mono<Boolean> triggerAndUpdateStatus(Authorization auth, Challenge ch, Order order, SSLChallenge challenge) {

        String chError = null;
        String chStatus = null;

        try {
            Tuple2<String, String> tup = this.triggerChallenge(auth, ch);
            chStatus = tup.getT1();
            if (!tup.getT2().isBlank())
                chError = tup.getT2();
        } catch (AcmeException e) {
            return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                SecurityMessageResourceService.TRIGGER_FAILED);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        try {
            order.update();
        } catch (AcmeException e) {
            return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                SecurityMessageResourceService.TRIGGER_FAILED);
        }

        return this.challengeDao.updateStatus(challenge.getId(), chStatus, chError);
    }

    private Tuple2<Authorization, Challenge> findChallengeAndAuthorization(Order order, SSLChallenge challenge) {

        for (Authorization auth : order.getAuthorizations()) {
            if (!auth.getIdentifier()
                .getDomain()
                .equals(challenge.getDomain()))
                continue;

            Challenge ch = auth.findChallenge(challenge.getChallengeType()
                    .equals(Http01Challenge.TYPE) ? Http01Challenge.TYPE : Dns01Challenge.TYPE)
                .orElse(null);

            if (ch != null) {
                return Tuples.of(auth, ch);
            }
        }
        return null;
    }

    private Tuple2<String, String> triggerChallenge(Authorization auth, Challenge ch)
        throws AcmeException, InterruptedException {

        String chStatus;
        String chError = "";

        // Check if the authorization is already invalid before triggering
        Status preStatus = auth.getStatus();
        logger.info("Pre-trigger auth status: {}, challenge status: {}, domain: {}, type: {}",
            preStatus, ch.getStatus(), auth.getIdentifier().getDomain(), ch.getType());

        if (preStatus == Status.INVALID) {
            chStatus = preStatus.toString();
            chError = "Authorization was already INVALID before triggering. A new certificate request with fresh challenges is needed.";
            logger.error("Authorization was already INVALID before triggering. Domain: {}", auth.getIdentifier().getDomain());
            return Tuples.of(chStatus, chError);
        }

        ch.trigger();
        int count = 10;
        Status status;

        while ((status = auth.getStatus()) != Status.VALID && status != Status.INVALID && count > 0) {

            Thread.sleep(5000L); // NOSONAR
            auth.update();
            count--;
        }

        chStatus = status.toString();

        if (status != Status.VALID) {
            // Update challenge to get the real status and error from ACME server
            try { ch.update(); } catch (AcmeException ignored) { /* best effort */ }

            String errorDetail = ch.getError()
                .map(this::formatProblemDetail)
                .orElse("No error detail returned by ACME server");

            logger.error("Challenge validation failed — Authorization status: {}, Challenge status: {}, Domain: {}, Challenge type: {}, Error: {}",
                status, ch.getStatus(), auth.getIdentifier().getDomain(), ch.getType(), errorDetail);

            chError = "Authorization status: " + status + " | Challenge status: " + ch.getStatus() + " | " + errorDetail;
        }

        return Tuples.of(chStatus, chError);
    }

    private String formatProblemDetail(Problem problem) {

        StringBuilder sb = new StringBuilder();

        sb.append("Type: ").append(problem.getType());

        problem.getTitle().ifPresent(title -> sb.append(" | Title: ").append(title));

        problem.getDetail().ifPresent(detail -> sb.append(" | Detail: ").append(detail));

        List<Problem> subProblems = problem.getSubProblems();
        if (!subProblems.isEmpty()) {
            sb.append(" | Sub-problems: [");
            for (int i = 0; i < subProblems.size(); i++) {
                Problem sub = subProblems.get(i);
                if (i > 0) sb.append("; ");
                sb.append("{");
                sub.getIdentifier().ifPresent(id -> sb.append("domain: ").append(id.getDomain()).append(", "));
                sb.append("type: ").append(sub.getType());
                sub.getDetail().ifPresent(detail -> sb.append(", detail: ").append(detail));
                sb.append("}");
            }
            sb.append("]");
        }

        return sb.toString();
    }

    private String buildOrderDiagnostics(Order order) {

        StringBuilder sb = new StringBuilder();
        sb.append("Order status: ").append(order.getStatus());

        order.getError().ifPresent(problem ->
            sb.append(" | Order error: ").append(formatProblemDetail(problem)));

        for (Authorization auth : order.getAuthorizations()) {
            sb.append(" | Auth [").append(auth.getIdentifier().getDomain()).append("]: status=").append(auth.getStatus());

            for (Challenge ch : auth.getChallenges()) {
                sb.append(", challenge(").append(ch.getType()).append(")=").append(ch.getStatus());
                ch.getError().ifPresent(problem ->
                    sb.append(" error=").append(formatProblemDetail(problem)));
            }
        }

        return sb.toString();
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<SSLCertificateOrder> readRequestByURLId(ULong urlId) {

        return FlatMapUtil.flatMapMono(

                () -> this.clientUrlService.read(urlId),

                clientUrl -> this.requestDao.readByURLId(urlId),

                (clientUrl, request) -> this.challengeDao.readChallengesByRequestId(request.getId()),

                (clientUrl, request, challenges) -> Mono.just(new SSLCertificateOrder().setRequest(request)
                    .setChallenges(challenges)))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.readRequestByURLId"));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<SSLCertificateOrder> createCertificateRequest(SSLCertificateOrderRequest request) {

        if (request.getUrlId() == null || request.getDomainNames()
            .isEmpty()
            || request.getDomainNames()
            .stream().anyMatch(String::isBlank)) {

            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                SecurityMessageResourceService.BAD_CERT_REQUEST);
        }

        // Here we are using the clientURLService to read the URL object which will
        // perform all the necessary checks like managed or not.
        return FlatMapUtil.flatMapMono(

                () -> this.requestDao.checkIfRequestExistOnURL(request.getUrlId())
                    .filter(exists -> exists)
                    .flatMap(
                        x -> this.msgService.throwMessage(msg -> new GenericException(HttpStatus.CONFLICT, msg),
                            SecurityMessageResourceService.REQUEST_EXISTING))
                    .defaultIfEmpty(false),

                e -> SecurityContextUtil.getUsersContextAuthentication(),

                (e, ca) -> ca.isSystemClient() ? Mono.just("")
                    : this.clientUrlService.read(request.getUrlId())
                    .map(ClientUrl::getUrlPattern)
                    .map(String::toLowerCase),

                (e, ca, url) -> validateDomainNames(request, url),

                (e, ca, url, valid) -> makeRecord(request).flatMap(this.requestDao::create),

                (e, ca, url, valid, sslRequest) -> this.createChallenges(sslRequest),

                (e, ca, url, valid, sslRequest, challenges) -> this.readRequestByURLId(request.getUrlId()))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createCertificateRequest"))
            .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT))
            .subscribeOn(Schedulers.boundedElastic());

    }

    public Mono<SSLCertificateOrder> createChallenges(ULong requestId) {

        return FlatMapUtil.flatMapMono(

                () -> this.requestDao.readById(requestId),

                this::createChallenges,

                (req, challenges) -> readRequestByURLId(req.getUrlId()))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createChallenges"))
            .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<List<SSLChallenge>> createChallenges(SSLRequest sslRequest) {

        return FlatMapUtil.flatMapMono(

                () -> this.challengeDao.deleteAllForRequest(sslRequest.getId()),

                deleted -> loginAndCreateNewOrder(sslRequest),

                (deleted, finOrder) -> this.requestDao.updateOrderUrl(sslRequest.getId(),
                    finOrder.getLocation().toString())
                    .thenReturn(finOrder),

                (deleted, finOrder, order) -> Flux.fromIterable(order.getAuthorizations())
                    .map(auth -> makeSSLChallenge(sslRequest, auth))
                    .flatMap(this.challengeDao::create)
                    .collectList())
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createChallenges"));
    }

    private Login getLogin() throws MalformedURLException, URISyntaxException {

        return new Session(this.sessionURL).login(new URI(this.accountURL).toURL(), accountKeyPair);
    }

    private Mono<Order> loginAndCreateNewOrder(SSLRequest sslRequest) {

        if (StringUtil.safeIsBlank(this.sessionURL) || StringUtil.safeIsBlank(this.accountURL)
            || StringUtil.safeIsBlank(this.accountKey)) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                SecurityMessageResourceService.LETS_ENCRYPT_CREDENTIALS);
        }

        Login login;
        try {
            login = getLogin();
        } catch (MalformedURLException | URISyntaxException e) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                SecurityMessageResourceService.LETS_ENCRYPT_CREDENTIALS);
        }

        Account account = login.getAccount();
        String[] domains = sslRequest.getDomains()
            .split(",");

        Order order;

        try {
            order = account.newOrder()
                .domains(domains)
                .create();
        } catch (AcmeException e) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                SecurityMessageResourceService.LETS_ENCRYPT_ISSUE, e.getMessage());
        }

        return Mono.just(order);
    }

    private Mono<Order> loginAndBindOrder(SSLRequest sslRequest) {

        if (StringUtil.safeIsBlank(this.sessionURL) || StringUtil.safeIsBlank(this.accountURL)
            || StringUtil.safeIsBlank(this.accountKey)) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                SecurityMessageResourceService.LETS_ENCRYPT_CREDENTIALS);
        }

        return this.requestDao.getOrderUrl(sslRequest.getId())
            .flatMap(orderUrl -> {

                if (StringUtil.safeIsBlank(orderUrl)) {
                    logger.warn("No stored order URL for request {}, creating new order", sslRequest.getId());
                    return this.loginAndCreateNewOrder(sslRequest);
                }

                Login login;
                try {
                    login = getLogin();
                } catch (MalformedURLException | URISyntaxException e) {
                    return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                        SecurityMessageResourceService.LETS_ENCRYPT_CREDENTIALS);
                }

                try {
                    Order order = login.bindOrder(new URI(orderUrl).toURL());
                    order.update();
                    return Mono.just(order);
                } catch (Exception e) {
                    logger.warn("Failed to bind to stored order URL {}, creating new order: {}", orderUrl, e.getMessage());
                    return this.loginAndCreateNewOrder(sslRequest);
                }
            });
    }

    private SSLChallenge makeSSLChallenge(SSLRequest sslRequest, Authorization auth) {
        SSLChallenge challenge = new SSLChallenge();

        Http01Challenge hch = auth.findChallenge(Http01Challenge.class)
            .orElse(null);

        if (hch != null) {
            challenge.setChallengeType(Http01Challenge.TYPE);
            challenge.setToken(hch.getToken());
            challenge.setAuthorization(hch.getAuthorization());
        } else {

            Dns01Challenge dch = auth.findChallenge(Dns01Challenge.class)
                .orElse(null);

            if (dch != null) {
                challenge.setChallengeType(Dns01Challenge.TYPE);
                challenge.setToken(Dns01Challenge.toRRName(auth.getIdentifier()
                    .getDomain()));
                challenge.setAuthorization(dch.getDigest());
            }
        }

        return challenge.setRequestId(sslRequest.getId())
            .setDomain(auth.getIdentifier()
                .getDomain())
            .setRetryCount(0)
            .setStatus(auth.getStatus()
                .toString());
    }

    private Mono<String> keyPairToString(KeyPair kp) {

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            KeyPairUtils.writeKeyPair(kp, new OutputStreamWriter(bos));
            return Mono.just(bos.toString());
        } catch (Exception ex) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                SecurityMessageResourceService.ERROR_KEY_CSR);
        }
    }

    private Mono<SSLRequest> makeRecord(SSLCertificateOrderRequest request) {

        KeyPair kp = KeyPairUtils.createKeyPair(2048);

        return FlatMapUtil.flatMapMono(

                () -> this.keyPairToString(kp),

                key -> {
                    SSLRequest rec = new SSLRequest().setCrtKey(key)
                        .setOrganization(request.getOrganizationName())
                        .setDomains(String.join(",", request.getDomainNames()))
                        .setUrlId(request.getUrlId())
                        .setValidity(request.getValidityInMonths());

                    try {
                        CSRBuilder csr = new CSRBuilder();
                        for (String domain : request.getDomainNames()) {
                            csr.addDomain(domain);
                        }
                        csr.setOrganization(request.getOrganizationName());
                        csr.sign(kp);
                        StringWriter sw = new StringWriter();
                        csr.write(sw);
                        rec.setCsr(sw.toString());
                    } catch (IOException ex) {
                        return this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
                            SecurityMessageResourceService.ERROR_KEY_CSR);
                    }

                    return Mono.just(rec);
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.makeRecord"));

    }

    private Mono<Boolean> validateDomainNames(SSLCertificateOrderRequest request, String url) {
        if (url.isBlank())
            return Mono.just(true);

        int slashIndex = url.indexOf("//");

        if (slashIndex != -1)
            url = url.substring(slashIndex + 2);

        slashIndex = url.indexOf("/");

        if (slashIndex != -1)
            url = url.substring(0, slashIndex);

        final String testURL = url;

        List<String> wrongURLs = request.getDomainNames()
            .stream()
            .filter(e -> !e.endsWith(testURL))
            .toList();

        if (wrongURLs.isEmpty())
            return Mono.just(true);

        return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
            SecurityMessageResourceService.MISMATCH_DOMAINS, wrongURLs.toString());
    }

    public Mono<List<SSLCertificateConfiguration>> getAllCertificates() {

        return this.cacheService.cacheValueOrGet(CACHE_NAME_CERTIFICATE, this.certificateDao::readAllCertificates,
            CACHE_CERTIFICATE_VALUE);
    }

    public Mono<String> getLastUpdated() {

        return this.cacheService.cacheValueOrGet(CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT,
            this.certificateDao::getLastUpdated, CACHE_CERTIFICATE_LAST_UPDATED_VALUE);
    }

    public Mono<Boolean> deleteRequestByURLId(ULong urlId) {

        return FlatMapUtil.flatMapMono(

                () -> this.clientUrlService.read(urlId),

                url -> this.requestDao.deleteByURLId(urlId))
            .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT))
            .subscribeOn(Schedulers.boundedElastic())
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.deleteRequestByURLId"));
    }

    public Mono<String> getToken(String token) {
        return this.challengeDao.getToken(token);
    }

    public Mono<Boolean> deleteCertificate(ULong id) {

        return FlatMapUtil.flatMapMono(

                () -> this.certificateDao.readById(id),

                crt -> this.clientUrlService.read(crt.getUrlId()),

                (crt, curl) -> this.certificateDao.delete(id)
                    .map(e -> e == 1))
            .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT))
            .subscribeOn(Schedulers.boundedElastic())
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.deleteCertificate"));
    }

    public Mono<SSLCertificateRenewalResult> renewExpiringCertificates(int daysBeforeExpiry) {

        return this.certificateDao.readExpiringCertificates(daysBeforeExpiry)
            .flatMapMany(Flux::fromIterable)
            .flatMap(cert -> this.renewSingleCertificate(cert)
                .onErrorResume(e -> {
                    logger.error("Failed to renew certificate for urlId {}: {}", cert.getUrlId(), e.getMessage(), e);
                    return Mono.just(new SingleRenewalOutcome(false,
                        "urlId=" + cert.getUrlId() + ": " + e.getMessage()));
                }), 1)
            .collectList()
            .map(outcomes -> {
                SSLCertificateRenewalResult result = new SSLCertificateRenewalResult();
                for (SingleRenewalOutcome o : outcomes) {
                    if (o.renewed()) result.setRenewedCount(result.getRenewedCount() + 1);
                    else {
                        result.setFailedCount(result.getFailedCount() + 1);
                        if (o.error() != null) result.getErrors().add(o.error());
                    }
                }
                return result;
            })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.renewExpiringCertificates"))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<SingleRenewalOutcome> renewSingleCertificate(SSLCertificate cert) {

        return FlatMapUtil.flatMapMono(

                () -> this.requestDao.readByURLId(cert.getUrlId()),

                request -> this.challengeDao.readChallengesByRequestId(request.getId()),

                (request, challenges) -> {
                    boolean allHttp01 = !challenges.isEmpty()
                        && challenges.stream().allMatch(c -> Http01Challenge.TYPE.equals(c.getChallengeType()));

                    if (allHttp01)
                        return this.renewHttp01Certificate(cert, request);
                    else
                        return this.createNewChallengesForNonHttp01(cert, request);
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.renewSingleCertificate"));
    }

    private Mono<SingleRenewalOutcome> renewHttp01Certificate(SSLCertificate cert, SSLRequest existingRequest) {

        return this.attemptRenewalWithRequest(existingRequest)
            .onErrorResume(e -> {
                logger.warn("Renewal with existing request failed for urlId {}, trying with new request: {}",
                    cert.getUrlId(), e.getMessage());
                return this.attemptRenewalWithNewRequest(cert, existingRequest);
            });
    }

    private Mono<SingleRenewalOutcome> attemptRenewalWithRequest(SSLRequest request) {

        return FlatMapUtil.flatMapMono(

                () -> this.createChallenges(request),

                challenges -> {
                    boolean allValid = challenges.stream()
                        .allMatch(c -> Status.VALID.toString().equalsIgnoreCase(c.getStatus()));

                    if (allValid)
                        return Mono.just(challenges);

                    return Flux.fromIterable(challenges)
                        .filter(c -> !Status.VALID.toString().equalsIgnoreCase(c.getStatus()))
                        .flatMap(c -> this.triggerChallenge(c, request).thenReturn(c))
                        .then(this.challengeDao.readChallengesByRequestId(request.getId()));
                },

                (challenges, updatedChallenges) -> {
                    boolean allValid = updatedChallenges.stream()
                        .allMatch(c -> Status.VALID.toString().equalsIgnoreCase(c.getStatus()));

                    if (allValid)
                        return this.createCertificateInternal(request.getId())
                            .map(success -> new SingleRenewalOutcome(true, null));

                    return Mono.just(new SingleRenewalOutcome(false,
                        "urlId=" + request.getUrlId() + ": challenges not all valid after trigger"));
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.attemptRenewalWithRequest"))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<SingleRenewalOutcome> attemptRenewalWithNewRequest(SSLCertificate cert, SSLRequest existingRequest) {

        SSLCertificateOrderRequest orderRequest = new SSLCertificateOrderRequest()
            .setUrlId(cert.getUrlId())
            .setDomainNames(List.of(existingRequest.getDomains().split(",")))
            .setOrganizationName(existingRequest.getOrganization())
            .setValidityInMonths(existingRequest.getValidity());

        return FlatMapUtil.flatMapMono(

                () -> this.requestDao.deleteByURLId(cert.getUrlId()),

                deleted -> this.makeRecord(orderRequest).flatMap(this.requestDao::create),

                (deleted, newRequest) -> this.attemptRenewalWithRequest(newRequest))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.attemptRenewalWithNewRequest"))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<SingleRenewalOutcome> createNewChallengesForNonHttp01(SSLCertificate cert, SSLRequest existingRequest) {

        return this.createChallenges(existingRequest)
            .<SingleRenewalOutcome>map(challenges -> new SingleRenewalOutcome(false, null))
            .onErrorResume(e -> {
                logger.warn("Challenge creation with existing request failed for urlId {}, trying with new request: {}",
                    cert.getUrlId(), e.getMessage());
                return this.createNewRequestWithChallenges(cert, existingRequest);
            });
    }

    private Mono<SingleRenewalOutcome> createNewRequestWithChallenges(SSLCertificate cert, SSLRequest existingRequest) {

        SSLCertificateOrderRequest orderRequest = new SSLCertificateOrderRequest()
            .setUrlId(cert.getUrlId())
            .setDomainNames(List.of(existingRequest.getDomains().split(",")))
            .setOrganizationName(existingRequest.getOrganization())
            .setValidityInMonths(existingRequest.getValidity());

        return FlatMapUtil.flatMapMono(

                () -> this.requestDao.deleteByURLId(cert.getUrlId()),

                deleted -> this.makeRecord(orderRequest).flatMap(this.requestDao::create),

                (deleted, newRequest) -> this.createChallenges(newRequest),

                (deleted, newRequest, challenges) -> Mono.just(new SingleRenewalOutcome(false, null)))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createNewRequestWithChallenges"))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private record SingleRenewalOutcome(boolean renewed, String error) {}
}
