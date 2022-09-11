package com.fincity.security.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.service.CacheService;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;
import com.fincity.security.model.ClientUrlPattern;

import reactor.core.publisher.Mono;

@Service
public class ClientUrlService
        extends AbstractJOOQUpdatableDataService<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO> {

	@Autowired
	private CacheService cacheService;
//
//	@Autowired
//	private MessageResourceService messageResourceService;

	private static final String CACHE_NAME_CLIENT_URL = "clientUrl";
	private static final String CACHE_CLIENT_URL_LIST = "list";

	@PreAuthorize("hasPermission('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> read(ULong id) {

//		return super.read(id).flatMap(clientUrl -> SecurityContextUtil.getUsersContextAuthentication()
//		        .flatMap(e ->
//				{
//			        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(e.getClientTypeCode()))
//				        return Mono.just(clientUrl);
//
//			        ContextUser user = e.getUser();
//			        ULong userClientId = ULong.valueOf(user.getClientId());
//
//			        if (clientUrl.getClientId()
//			                .equals(userClientId))
//				        return Mono.just(clientUrl);
//
//			        return clientService.getPotentialClientList(id)
//			                .filter(lst -> lst.contains(userClientId))
//			                .map(x -> clientUrl)
//			                .switchIfEmpty(Mono.defer(() -> this.messageResourceService.getMessage(MessageResourceService.OBJECT_NOT_FOUND)
//			                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
//			                                StringFormatter.format(msg, "Client URL", id))))));
//		        }));
		return super.read(id);
	}

	@PreAuthorize("hasPermission('Authorities.Client_UPDATE')")
	@Override
	public Mono<Page<ClientUrl>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasPermission('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> create(ClientUrl entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

			        ULong clientId = ULong.valueOf(ca.getUser()
			                .getClientId());

			        if (entity.getClientId() == null)
				        entity.setClientId(clientId);
			        else {
				        if (!ca.getClientTypeCode()
				                .equals(ContextAuthentication.CLIENT_TYPE_SYSTEM)) {
					        return super.create(entity);
				        }
			        }

			        return super.create(entity);
		        });
	}

	@PreAuthorize("hasPermission('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> update(ClientUrl entity) {

		return super.update(entity);
	}

	@PreAuthorize("hasPermission('Authorities.Client_UPDATE')")
	@Override
	public Mono<ClientUrl> update(ULong key, Map<String, Object> updateFields) {

		return super.update(key, updateFields);
	}

	@PreAuthorize("hasPermission('Authorities.Client_UPDATE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return this.read(id)
		        .flatMap(e -> super.delete(id));
	}

	public Mono<List<ClientUrlPattern>> readAllAsClientURLPattern() {

		Mono<List<ClientUrlPattern>> curl = cacheService.get(CACHE_NAME_CLIENT_URL, CACHE_CLIENT_URL_LIST);
		return curl.switchIfEmpty(Mono.defer(() -> this.dao.readAll(null)
				.map(ClientUrl::toClientUrlPattern)
		        .collectList()));
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
