package com.fincity.security.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemReader;
import org.jooq.types.ULong;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
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

	@Autowired
	private SecurityMessageResourceService msgService;

	@Autowired
	private SSLCertificateDAO certificateDao;

	@Autowired
	private SSLRequestDAO requestDao;

	@Autowired
	private SSLChallengeDAO challengeDao;

	@Autowired
	private ClientUrlService clientUrlService;

	@Autowired
	private CacheService cacheService;

	@Value("${letsencrypt.session:}")
	private String sessionURL;

	@Value("${letsencrypt.login:}")
	private String accountURL;

	@Value("${letsencrypt.key:}")
	private String accountKey;

	private KeyPair accountKeyPair;

	@PostConstruct
	private void initialize() {

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

				() -> this.clientUrlService.read(certificate.getUrlId()),

				clientUrl -> this.certificateDao.create(certificate)

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createCertificate"))
				.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_CERTIFICATE))
				.subscribeOn(Schedulers.boundedElastic());
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	public Mono<Boolean> createCertificate(ULong requestId) {

		return FlatMapUtil.flatMapMono(

				() -> this.requestDao.readById(requestId),

				request -> this.clientUrlService.read(request.getUrlId()),

				(request, clientUrl) -> this.loginAndGetOrder(request),

				(request, clientUrl, order) -> {

					try {

						order.update();

						PemReader reader = new PemReader(new StringReader(request.getCsr()));
						PKCS10CertificationRequest csr = new PKCS10CertificationRequest(reader.readPemObject()
								.getContent());

						order.execute(csr.getEncoded());

						int attempts = 10;
						while (order.getStatus() != Status.VALID && attempts-- > 0) {

							if (order.getStatus() == Status.INVALID) {
								break;
							}

							Thread.sleep(3000L);
							order.update();
						}
					} catch (InterruptedException ex) {

						Thread.currentThread()
								.interrupt();
					} catch (AcmeException | IOException ex) {
						return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, ex,
								SecurityMessageResourceService.LETS_ENCRYPT_ISSUE, ex.getMessage());
					}

					return Mono.just(order.getCertificate());
				},

				(request, clientUrl, order, certificate) -> this.certificateDao.create(request, certificate)

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createCertificate"))
				.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_CERTIFICATE))
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
				.subscribeOn(Schedulers.boundedElastic());

	}

	private Mono<Boolean> triggerChallenge(SSLChallenge challenge, SSLRequest request) {

		return FlatMapUtil.flatMapMono(

				() -> this.loginAndGetOrder(request),

				order -> {

					var authChallengeTup = this.findChallengeAndAuthorization(order, challenge);

					String chError = null;
					String chStatus = null;

					try {

						Tuple2<String, String> tup = this.triggerChallenge(authChallengeTup.getT1(),
								authChallengeTup.getT2());
						chStatus = tup.getT1();
						if (!tup.getT2()
								.isBlank())
							chError = tup.getT2();
					} catch (AcmeException e) {
						return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, e,
								SecurityMessageResourceService.TRIGGER_FAILED);
					} catch (InterruptedException ie) {
						Thread.currentThread()
								.interrupt();
					}

					try {
						order.update();
					} catch (AcmeException e) {
						return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, e,
								SecurityMessageResourceService.TRIGGER_FAILED);
					}

					return this.challengeDao.updateStatus(challenge.getId(), chStatus, chError);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.readRequestByURLId"))
				.subscribeOn(Schedulers.boundedElastic());
	}

	private Tuple2<Authorization, Challenge> findChallengeAndAuthorization(Order order, SSLChallenge challenge) {

		for (Authorization auth : order.getAuthorizations()) {
			if (!auth.getIdentifier()
					.getDomain()
					.equals(challenge.getDomain()))
				continue;

			Challenge ch = auth
					.findChallenge(challenge.getChallengeType().equals(Http01Challenge.TYPE)
							? Http01Challenge.TYPE
							: Dns01Challenge.TYPE)
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

		ch.trigger();
		int count = 2;
		Status status;

		while ((status = auth.getStatus()) != Status.VALID && count > 0) {

			Thread.sleep(3000L);
			auth.update();
			count--;
		}

		chStatus = status.toString();

		if (status != Status.VALID) {
			chError = ch.getError()
					.map(Object::toString)
					.orElse("Unknown error");
		}

		return Tuples.of(chStatus, chError);
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
						.stream()
						.filter(String::isBlank)
						.count() != 0l) {

			return this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
					SecurityMessageResourceService.BAD_CERT_REQUEST);
		}

		// Here we are using the clientURLService to read the URL object which will
		// perform all the necessary checks like managed or not.
		return FlatMapUtil.flatMapMono(

				() -> this.requestDao.checkIfRequestExistOnURL(request.getUrlId())
						.flatMap(
								exists -> exists.booleanValue()
										? this.msgService.throwMessage(HttpStatus.CONFLICT,
												SecurityMessageResourceService.REQUEST_EXISTING)
										: Mono.just(false)),

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
				.subscribeOn(Schedulers.boundedElastic());

	}

	public Mono<SSLCertificateOrder> createChallenges(ULong requestId) {

		return FlatMapUtil.flatMapMono(

				() -> this.requestDao.readById(requestId),

				this::createChallenges,

				(req, challenges) -> this.readRequestByURLId(req.getUrlId()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createChallenges"))
				.subscribeOn(Schedulers.boundedElastic());
	}

	private Mono<List<SSLChallenge>> createChallenges(SSLRequest sslRequest) {

		return FlatMapUtil.flatMapMono(

				() -> this.challengeDao.deleteAllForRequest(sslRequest.getId()),

				deleted -> loginAndGetOrder(sslRequest),

				(deleted, finOrder) -> Flux.fromIterable(finOrder.getAuthorizations())
						.map(auth -> makeSSLChallenge(sslRequest, auth))
						.flatMap(this.challengeDao::create)
						.collectList())
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.createChallenges"));
	}

	private Mono<Order> loginAndGetOrder(SSLRequest sslRequest) {

		if (StringUtil.safeIsBlank(this.sessionURL) || StringUtil.safeIsBlank(this.accountURL)
				|| StringUtil.safeIsBlank(this.accountKey)) {
			return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
					SecurityMessageResourceService.LETS_ENCRYPT_CREDENTIALS);
		}

		Session session = new Session(this.sessionURL);
		Login login;
		try {
			login = session.login(new URI(this.accountURL).toURL(), accountKeyPair);
		} catch (MalformedURLException | URISyntaxException e) {
			return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, e,
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
			return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, e,
					SecurityMessageResourceService.LETS_ENCRYPT_ISSUE, e.getMessage());
		}

		return Mono.just(order);
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
			return Mono.just(new String(bos.toByteArray()));
		} catch (Exception ex) {
			return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
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
							.setDomains(request.getDomainNames()
									.stream()
									.collect(Collectors.joining(",")))
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
					} catch (Exception ex) {
						return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
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

		return this.msgService.throwMessage(HttpStatus.BAD_REQUEST, SecurityMessageResourceService.MISMATCH_DOMAINS,
				wrongURLs.toString());
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
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SSLCertificateService.deleteRequestByURLId"))
				.subscribeOn(Schedulers.boundedElastic());
	}

	public Mono<String> getToken(String token) {
		return this.challengeDao.getToken(token);
	}
}
