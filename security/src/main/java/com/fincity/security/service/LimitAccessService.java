package com.fincity.security.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.LimitAccessDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppLimitationsRecord;

import reactor.core.publisher.Mono;

@Service
public class LimitAccessService
        extends AbstractJOOQUpdatableDataService<SecurityAppLimitationsRecord, ULong, LimitAccess, LimitAccessDAO> {

	private static final String LIMIT = "limit";

	private static final String SEPERATOR = "_";

	@Autowired
	private CacheService cacheService;

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
		return super.create(entity);
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_UPDATE')")
	public Mono<LimitAccess> update(LimitAccess entity) {
		return super.update(entity);
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Limitations_DELETE')")
	public Mono<Integer> delete(ULong id) {
		return super.delete(id);
	}

}
