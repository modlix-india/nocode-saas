package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClient.*;
import static com.fincity.security.jooq.tables.SecurityClientUrl.*;
import static com.fincity.security.jooq.tables.SecuritySslCertificate.*;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.jooq.Record1;
import org.jooq.Record6;
import org.jooq.SelectConditionStep;
import org.jooq.UpdateConditionStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.shredzone.acme4j.Certificate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.security.dto.SSLCertificate;
import com.fincity.security.dto.SSLRequest;
import com.fincity.security.jooq.tables.records.SecuritySslCertificateRecord;
import com.fincity.security.model.SSLCertificateConfiguration;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

@Component
public class SSLCertificateDAO extends AbstractUpdatableDAO<SecuritySslCertificateRecord, ULong, SSLCertificate> {

	private static final String LETS_ENCRYPT = "Lets Encrypt";

	private final SecurityMessageResourceService msgResourceService;

	protected SSLCertificateDAO(SecurityMessageResourceService msgResourceService) {
		super(SSLCertificate.class, SECURITY_SSL_CERTIFICATE, SECURITY_SSL_CERTIFICATE.ID);
		this.msgResourceService = msgResourceService;
	}

	@Override
	public Mono<SSLCertificate> create(SSLCertificate pojo) {

		return super.create(pojo).subscribeOn(Schedulers.boundedElastic()).flatMap(e -> {

			Mono.just(Tuples.of(e.getId(), e.getUrlId())).delayElement(Duration.ofSeconds(10))
					.flatMap(tuple -> this.makeRestOfNotCurrent(tuple.getT1(), tuple.getT2()))
					.subscribeOn(Schedulers.boundedElastic())
					.onErrorResume((err) -> {
						logger.error("Error while updating rest of the not current certificates for url id {}",
								pojo.getUrlId(), err);
						return Mono.empty();
					}).subscribe(value -> {
						logger.info("Updated rest of the not current certificates for url id {} with status : {}",
								pojo.getUrlId(), value);
					});

			return Mono.just(e);
		});
	}

	private Mono<Boolean> makeRestOfNotCurrent(ULong certId, ULong urlId) {

		return Mono.from(this.dslContext.transactionPublisher(trx -> {

			UpdateConditionStep<SecuritySslCertificateRecord> query = DSL.using(trx)
					.update(SECURITY_SSL_CERTIFICATE)
					.set(SECURITY_SSL_CERTIFICATE.CURRENT, ByteUtil.ZERO)
					.where(DSL.and(SECURITY_SSL_CERTIFICATE.ID.ne(certId),
							SECURITY_SSL_CERTIFICATE.URL_ID.eq(urlId)));

			return Mono.from(query);
		})).map(e -> e > 0);
	}

	public Mono<SSLCertificate> create(SSLRequest request, Certificate certificate) {

		var crt = certificate.getCertificate();

		StringWriter sw = new StringWriter();
		try {
			certificate.writeCertificate(sw);
		} catch (IOException e) {
			return this.msgResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
					SecurityMessageResourceService.CERTIFICATE_PROBLEM);
		}

		return this.create(new SSLCertificate()

				.setAutoRenewTill(LocalDateTime.from(Instant.now()
						.plus(Duration.ofDays(request.getValidity() * 30l))
						.atOffset(ZoneOffset.UTC)))

				.setCrt(sw.toString())

				.setCrtChain("")

				.setCrtKey(request.getCrtKey())

				.setCsr(request.getCsr())

				.setCurrent(true)

				.setDomains(request.getDomains())

				.setIssuer(CommonsUtil.nonNullValue(crt.getIssuerX500Principal()
						.getName(X500Principal.CANONICAL), LETS_ENCRYPT))

				.setOrganization(request.getOrganization())

				.setUrlId(request.getUrlId())

				.setExpiryDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(crt.getNotAfter()
						.getTime()), ZoneOffset.UTC)));
	}

	public Mono<List<SSLCertificateConfiguration>> readAllCertificates() {

		SelectConditionStep<Record6<String, String, String, String, String, String>> query = this.dslContext
				.select(SECURITY_SSL_CERTIFICATE.CRT, SECURITY_SSL_CERTIFICATE.CRT_KEY,
						SECURITY_SSL_CERTIFICATE.CRT_CHAIN, SECURITY_CLIENT_URL.APP_CODE,
						SECURITY_CLIENT_URL.URL_PATTERN, SECURITY_CLIENT.CODE)
				.from(SECURITY_CLIENT_URL)
				.leftJoin(SECURITY_SSL_CERTIFICATE)
				.on(SECURITY_CLIENT_URL.ID.eq(SECURITY_SSL_CERTIFICATE.URL_ID))
				.leftJoin(SECURITY_CLIENT)
				.on(SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID))
				.where(DSL.or(SECURITY_SSL_CERTIFICATE.CURRENT.eq(ByteUtil.ONE),
						SECURITY_SSL_CERTIFICATE.CURRENT.isNull()));
		return Flux.from(query)
				.map(e -> {

					String certificate = e.get(SECURITY_SSL_CERTIFICATE.CRT_CHAIN);

					if (certificate != null) {

						String crt = e.get(SECURITY_SSL_CERTIFICATE.CRT);
						certificate = crt + (crt.endsWith("\n") ? "" : "\n") + certificate;
					}

					String pattern = e.get(SECURITY_CLIENT_URL.URL_PATTERN);
					if (pattern != null) {
						int index = pattern.indexOf("//");
						if (index != -1)
							pattern = pattern.substring(index + 2);
						index = pattern.indexOf(":");
						if (index != -1)
							pattern = pattern.substring(0, index);
					}

					return new SSLCertificateConfiguration().setAppCode(e.get(SECURITY_CLIENT_URL.APP_CODE))
							.setClientCode(e.get(SECURITY_CLIENT.CODE))
							.setPrivateKey(e.get(SECURITY_SSL_CERTIFICATE.CRT_KEY))
							.setUrl(pattern)
							.setCertificate(certificate);
				})
				.collectList();
	}

	public Mono<String> getLastUpdated() {

		return FlatMapUtil.flatMapMonoWithNull(

				() -> Mono.from(this.dslContext.select(SECURITY_SSL_CERTIFICATE.UPDATED_AT)
						.from(SECURITY_SSL_CERTIFICATE)
						.orderBy(SECURITY_SSL_CERTIFICATE.UPDATED_AT.desc())
						.limit(1))
						.map(Record1::value1),

				certLast -> Mono.from(this.dslContext.select(SECURITY_CLIENT_URL.UPDATED_AT)
						.from(SECURITY_CLIENT_URL)
						.orderBy(SECURITY_CLIENT_URL.UPDATED_AT.desc())
						.limit(1))
						.map(Record1::value1),

				(certLast, urlLast) -> {

					if (certLast == null && urlLast == null)
						return Mono.just("");
					if (certLast == null)
						return Mono.just(Long.toString(urlLast.toInstant(ZoneOffset.UTC)
								.getEpochSecond()));

					long cLast = certLast.toInstant(ZoneOffset.UTC)
							.getEpochSecond();
					long uLast = urlLast.toInstant(ZoneOffset.UTC)
							.getEpochSecond();

					return Mono.just(Long.toString(cLast > uLast ? cLast : uLast));
				});
	}
}
