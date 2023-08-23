package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;
import static com.fincity.security.jooq.tables.SecuritySslCertificate.SECURITY_SSL_CERTIFICATE;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.jooq.TableField;
import org.jooq.UpdateConditionStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.shredzone.acme4j.Certificate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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

@Component
public class SSLCertificateDAO extends AbstractUpdatableDAO<SecuritySslCertificateRecord, ULong, SSLCertificate> {

	private static final String LETS_ENCRYPT = "Lets Encrypt";
	@Autowired
	private SecurityMessageResourceService msgResourceService;

	protected SSLCertificateDAO() {
		super(SSLCertificate.class, SECURITY_SSL_CERTIFICATE, SECURITY_SSL_CERTIFICATE.ID);
	}

	@Override
	public Mono<SSLCertificate> create(SSLCertificate pojo) {

		return super.create(pojo).flatMap(this::makeRestOfNotCurrent);
	}

	private Mono<SSLCertificate> makeRestOfNotCurrent(SSLCertificate cert) {

		UpdateConditionStep<SecuritySslCertificateRecord> query = this.dslContext.update(SECURITY_SSL_CERTIFICATE)
				.set(SECURITY_SSL_CERTIFICATE.CURRENT, ByteUtil.ZERO)
				.where(DSL.and(SECURITY_SSL_CERTIFICATE.ID.ne(cert.getId()),
						SECURITY_SSL_CERTIFICATE.URL_ID.eq(cert.getUrlId())));

		return Mono.from(query)
				.map(e -> cert);
	}

	public Mono<SSLCertificate> create(SSLRequest request, Certificate certificate) {

		var crt = certificate.getCertificate();

		StringWriter sw = new StringWriter();
		try {
			certificate.writeCertificate(sw);
		} catch (IOException e) {
			return this.msgResourceService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, e,
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

		return Flux
				.from(this.dslContext
						.select(SECURITY_SSL_CERTIFICATE.CRT, SECURITY_SSL_CERTIFICATE.CRT_KEY,
								SECURITY_SSL_CERTIFICATE.CRT_CHAIN, SECURITY_CLIENT_URL.APP_CODE,
								SECURITY_CLIENT_URL.URL_PATTERN, SECURITY_CLIENT.CODE)
						.from(SECURITY_CLIENT_URL)
						.join(SECURITY_SSL_CERTIFICATE)
						.on(SECURITY_CLIENT_URL.ID.eq(SECURITY_SSL_CERTIFICATE.URL_ID))
						.join(SECURITY_CLIENT)
						.on(SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID))
						.where(DSL.or(SECURITY_SSL_CERTIFICATE.CURRENT
								.eq(ByteUtil.ONE), DSL.and(SECURITY_SSL_CERTIFICATE.CURRENT.isNull()))))
				.map(e -> {

					String certificate = e.get(SECURITY_SSL_CERTIFICATE.CRT_CHAIN);

					if (certificate != null) {

						String crt = e.get(SECURITY_SSL_CERTIFICATE.CRT);
						certificate = crt + (crt.endsWith("\n") ? "" : "\n") + certificate;
					}

					return new SSLCertificateConfiguration().setAppCode(e.get(SECURITY_CLIENT_URL.APP_CODE))
							.setClientCode(e.get(SECURITY_CLIENT.CODE))
							.setPrivateKey(e.get(SECURITY_SSL_CERTIFICATE.CRT_KEY))
							.setUrl(e.get(SECURITY_CLIENT_URL.URL_PATTERN))
							.setCertificate(certificate);
				})
				.collectList();
	}

	public Mono<String> getLastUpdated() {

		TableField<SecuritySslCertificateRecord, LocalDateTime> field = SECURITY_SSL_CERTIFICATE.UPDATED_AT;

		return Mono.from(this.dslContext.select(field)
				.from(SECURITY_SSL_CERTIFICATE)
				.orderBy(field.desc())
				.limit(1))
				.map(e -> e.value1()
						.toInstant(ZoneOffset.UTC)
						.getEpochSecond())
				.map(Object::toString);
	}
}
