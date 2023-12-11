package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.LimitOwnerAccessDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppOwnerLimitationsRecord;

import reactor.core.publisher.Mono;

@Service
public class LimitOwnerAccessService extends
        AbstractJOOQUpdatableDataService<SecurityAppOwnerLimitationsRecord, ULong, LimitAccess, LimitOwnerAccessDAO> {

	private static final String LIMIT = "limit";

	private static final String SEPERATOR = "_";

	@Autowired
	private CacheService cacheService;

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

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_CREATE')")
	public Mono<LimitAccess> create(LimitAccess entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{
			        if (!ca.isSystemClient())
				        return Mono.empty();

			        return super.create(entity);
		        })
		        .switchIfEmpty(this.messageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
		                SecurityMessageResourceService.ONLY_SYS_USER_ACTION, "create", entity.getName()));

	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_UPDATE')")
	public Mono<LimitAccess> update(LimitAccess entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{
			        if (!ca.isSystemClient())
				        return Mono.empty();

			        return super.update(entity);
		        })
		        .switchIfEmpty(this.messageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
		                SecurityMessageResourceService.ONLY_SYS_USER_ACTION, "update", entity.getName()));

	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_DELETE')")
	public Mono<Integer> delete(ULong id) {

		Mono<LimitAccess> entity = super.read(id);

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{
			        if (!ca.isSystemClient())
				        return Mono.empty();

			        return entity;
		        },

		        (ca, ent) -> super.delete(id)

		)
		        .switchIfEmpty(this.messageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
		                SecurityMessageResourceService.ONLY_SYS_USER_ACTION, "delete", ""));

	}

	public Mono<Long> readByAppandClientId(ULong appId, ULong clientId, String objectName) {

		return this.cacheService.cacheValueOrGet(
		        appId.toString() + SEPERATOR + clientId.toString() + SEPERATOR + objectName,
		        () -> this.dao.getByAppandClientId(appId, clientId, objectName));

	}
}
