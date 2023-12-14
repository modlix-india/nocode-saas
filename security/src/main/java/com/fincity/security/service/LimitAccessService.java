package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.LimitAccessDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppLimitationsRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class LimitAccessService
        extends AbstractJOOQUpdatableDataService<SecurityAppLimitationsRecord, ULong, LimitAccess, LimitAccessDAO> {

	private static final String LIMIT = "limit";

	private static final String SEPERATOR = "_";

	@Autowired
	private CacheService cacheService;

	@Autowired
	@Lazy
	private AppService appService;
	
	@Autowired
	private SecurityMessageResourceService messageResourceService;

	@Override
	protected Mono<LimitAccess> updatableEntity(LimitAccess entity) {

		return this.read(entity.getId())
		        .map(e -> e.setLimit(entity.getLimit()));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		HashMap<String, Object> map = new HashMap<>();

		if (fields == null)
			return Mono.just(map);

		map.put(LIMIT, fields.get(LIMIT));
		return Mono.just(map);
	}

	public Mono<Long> readByAppandClientId(ULong appId, ULong clientId, String objectName) {

		return cacheService.cacheValueOrGet(appId.toString() + SEPERATOR + clientId.toString() + SEPERATOR + objectName,
		        () -> this.dao.getByAppandClientId(appId, clientId, objectName));

	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_CREATE')")
	public Mono<LimitAccess> create(LimitAccess entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(entity.getClientId() == ULongUtil.valueOf(ca.getUser()
		                .getClientId()))
		                .flatMap(e -> Mono.just(e.booleanValue() ? e
		                        : entity.getClientId() == ULongUtil.valueOf(ca.getLoggedInFromClientId()))),

		        (ca, sameClient) -> sameClient.booleanValue() ? Mono.just(sameClient)
		                : this.appService.read(entity.getAppId())
		                        .flatMap(e -> Mono.just(entity.getClientId() == e.getClientId())),

		        (ca, sameClient, valid) ->
				{
			        if (!valid.booleanValue())
				        return Mono.empty();

			        return super.create(entity);
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "LimitAccessService.create"))
		        .switchIfEmpty(
		                this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                        SecurityMessageResourceService.FORBIDDEN_CREATE, LIMIT));

	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_UPDATE')")
	public Mono<LimitAccess> update(LimitAccess entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(entity.getClientId() == ULongUtil.valueOf(ca.getUser()
		                .getClientId()))
		                .flatMap(e -> Mono.just(e.booleanValue() ? e
		                        : entity.getClientId() == ULongUtil.valueOf(ca.getLoggedInFromClientId()))),

		        (ca, sameClient) -> sameClient.booleanValue() ? Mono.just(sameClient)
		                : this.appService.read(entity.getAppId())
		                        .flatMap(e -> Mono.just(entity.getClientId() == e.getClientId())),

		        (ca, sameClient, valid) ->
				{
			        if (!valid.booleanValue())
				        return Mono.empty();

			        return super.update(entity);
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "LimitAccessService.update"))
		        .switchIfEmpty(
		                this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                        SecurityMessageResourceService.FORBIDDEN_UPDATE, LIMIT));

	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_DELETE')")
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> super.read(id),

		        (ca, entity) -> Mono.just(entity.getClientId() == ULongUtil.valueOf(ca.getUser()
		                .getClientId()))
		                .flatMap(e -> Mono.just(e.booleanValue() ? e
		                        : entity.getClientId() == ULongUtil.valueOf(ca.getLoggedInFromClientId()))),

		        (ca, entity, sameClient) ->  ((Boolean) sameClient).booleanValue() ? Mono.just(sameClient)
		                : this.appService.read(entity.getAppId())
		                        .flatMap(e -> Mono.just(entity.getClientId() == e.getClientId())),

		        (ca, entity, sameClient, valid) ->
				{

			        if (!((Boolean) valid).booleanValue())
				        return Mono.empty();

			        return super.delete(id);
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "LimitAccessService.delete"))
		        .switchIfEmpty(
		                this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
		                        SecurityMessageResourceService.FORBIDDEN_DELETE, LIMIT));

	}

}
