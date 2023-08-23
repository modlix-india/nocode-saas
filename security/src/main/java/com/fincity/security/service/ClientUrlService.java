package com.fincity.security.service;

import static com.fincity.security.service.ClientService.CACHE_NAME_CLIENT_URL;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientUrlService
		extends AbstractJOOQUpdatableDataService<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO> {

	private static final String CLIENT_URL = "Client URL";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private SecurityMessageResourceService msgService;

	@Autowired
	private ClientService clientService;

	private static final String CACHE_NAME_CLIENT_URI = "uri";

	// This is used in gateway
	private static final String CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE = "gatewayClientAppCode";

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> read(ULong id) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> super.read(id),

				(ca, cu) -> {

					if (ca.isSystemClient() || ca.getUser()
							.getClientId()
							.equals(cu.getClientId()
									.toBigInteger()))
						return Mono.just(true);

					return clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
							.getClientId()), cu.getClientId());
				},

				(ca, cu, hasAccess) -> {
					if (hasAccess.booleanValue())
						return Mono.just(cu);
					return Mono.empty();
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.read"))
				.switchIfEmpty(msgService.throwMessage(HttpStatus.NOT_FOUND,
						SecurityMessageResourceService.OBJECT_NOT_FOUND, CLIENT_URL, id));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<Page<ClientUrl>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> create(ClientUrl entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isSystemClient() || entity.getClientId() == null || ca.getUser()
							.getClientId()
							.equals(entity.getClientId()
									.toBigInteger()))
						return Mono.just(true);

					return clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
							.getClientId()), entity.getClientId());
				},

				(ca, hasAccess) -> hasAccess.booleanValue() ? Mono.just(entity) : Mono.empty(),

				(ca, hasAccess, ent) -> {

					ULong clientId = ULong.valueOf(ca.getUser()
							.getClientId());

					if (ent.getClientId() == null)
						ent.setClientId(clientId);

					return super.create(ent);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.read"))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> update(ClientUrl entity) {

		return super.update(entity).flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> update(ULong key, Map<String, Object> updateFields) {

		return super.update(key, updateFields).flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return this.read(id)
				.flatMap(e -> super.delete(id))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		HashMap<String, Object> map = new HashMap<>();
		if (fields == null)
			return Mono.just(map);

		map.put("urlPattern", fields.get("urlPattern"));

		return Mono.just(map);
	}

	@Override
	protected Mono<ClientUrl> updatableEntity(ClientUrl entity) {

		return this.read(entity.getId())
				.map(e -> e.setUrlPattern(entity.getUrlPattern()));
	}

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser()
				.map(ContextUser::getId)
				.map(ULong::valueOf);
	}
}