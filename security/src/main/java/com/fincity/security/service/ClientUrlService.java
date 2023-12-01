package com.fincity.security.service;

import static com.fincity.security.service.ClientService.CACHE_NAME_CLIENT_URL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientUrlService
		extends AbstractJOOQUpdatableDataService<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO> {

	private static final String URL_PATTERN = "urlPattern";

	private static final String CLIENT_URL = "Client URL";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private SecurityMessageResourceService msgService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private AppService appService;

	private static final String CACHE_NAME_CLIENT_URI = "uri";

	// This is used in gateway
	private static final String CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE = "gatewayClientAppCode";
	
	private static final String HTTPS = "https://"; 
	
	private static final String SLASH ="/";

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> read(ULong id) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> super.read(id),

				(ca, cu) -> {

					if (ca.isSystemClient() || ca.getUser().getClientId().equals(cu.getClientId().toBigInteger()))
						return Mono.just(true);

					return clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), cu.getClientId());
				},

				(ca, cu, hasAccess) -> {
					if (hasAccess.booleanValue())
						return Mono.just(cu);
					return Mono.empty();
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.read"))
				.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
						SecurityMessageResourceService.OBJECT_NOT_FOUND, CLIENT_URL, id));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<Page<ClientUrl>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return super.readPageFilter(pageable, condition);
	}

	private String trimBackSlash(String str) {

		if (StringUtil.safeIsBlank(str))
			return str;

		String nStr = str.trim();

		if (!nStr.endsWith("/"))
			return nStr;

		StringBuilder sb = new StringBuilder(nStr);

		char x = sb.charAt(sb.length() - 1);

		while (x == '/' || x == ' ') {
			sb.delete(sb.length() - 1, sb.length());
		}

		return sb.toString();
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> create(ClientUrl entity) {

		entity.setUrlPattern(trimBackSlash(entity.getUrlPattern()));

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isSystemClient() || entity.getClientId() == null
							|| ca.getUser().getClientId().equals(entity.getClientId().toBigInteger()))
						return Mono.just(true);

					return clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()),
							entity.getClientId());
				},

				(ca, hasAccess) -> hasAccess.booleanValue() ? Mono.just(entity) : Mono.empty(),

				(ca, hasAccess, ent) -> {

					ULong clientId = ULong.valueOf(ca.getUser().getClientId());

					if (ent.getClientId() == null)
						ent.setClientId(clientId);

					return super.create(ent);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.read"))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> update(ClientUrl entity) {

		entity.setUrlPattern(trimBackSlash(entity.getUrlPattern()));

		return super.update(entity).flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> update(ULong key, Map<String, Object> updateFields) {

		if (updateFields.get(URL_PATTERN) != null)
			updateFields.put(URL_PATTERN, trimBackSlash(updateFields.get(URL_PATTERN).toString()));

		return super.update(key, updateFields).flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
				.flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
				.flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return this.read(id).flatMap(e -> super.delete(id))
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

		map.put(URL_PATTERN, fields.get(URL_PATTERN));

		return Mono.just(map);
	}

	@Override
	protected Mono<ClientUrl> updatableEntity(ClientUrl entity) {

		return this.read(entity.getId()).map(e -> e.setUrlPattern(entity.getUrlPattern()));
	}

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
	}

	public Mono<List<String>> getUrlsBasedOnApp(String appCode, String suffix) {

		if (StringUtil.safeIsBlank(appCode))
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.MANDATORY_APP_CODE);

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(ULongUtil.valueOf(ca.getLoggedInFromClientId())),

				(ca, clientId) -> this.dao.getClientUrlsBasedOnAppAndClient(appCode, clientId, ca.isSystemClient()),

				(ca, clientId, urlList) -> this.appService.getAppByCode(appCode),

				(ca, clientId, urlList, app) -> {

					if (!StringUtil.safeIsBlank(suffix)) {

						if (app.getClientId().equals(clientId))
							urlList.add(HTTPS + appCode + suffix + SLASH);
						else
							urlList.add(HTTPS + appCode + suffix + SLASH + ca.getLoggedInFromClientCode() + SLASH
									+ "page" + SLASH);
					}

					return Mono.just(urlList);
				}

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getUrlsBasedOnApp"));

	}
}