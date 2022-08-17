package com.fincity.security.service;

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

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.exception.GenericException;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;
import com.fincity.security.jwt.ContextAuthentication;
import com.fincity.security.model.ClientUrlPattern;
import com.fincity.security.model.condition.AbstractCondition;
import com.fincity.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

@Service
public class ClientUrlService
        extends AbstractUpdatableDataService<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO> {

	@Autowired
	private ClientService clientService;

	@Autowired
	private CacheService cacheService;

	@Autowired
	private MessageResourceService messageResourceService;

	private static final String CACHE_NAME_CLIENT_URL = "clientUrl";
	private static final String CACHE_CLIENT_URL_LIST = "list";

	@PreAuthorize("hasPermission('Client UPDATE')")
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
//			                .switchIfEmpty(Mono.defer(() -> this.messageResourceService.getMessage("object_not_found")
//			                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
//			                                StringFormatter.format(msg, "Client URL", id))))));
//		        }));
		return super.read(id);
	}

	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<Page<ClientUrl>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasPermission('Client UPDATE')")
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

					        return clientService.isBeingManagedBy(clientId, entity.getClientId())
					                .flatMap(managed ->
									{
						                if (Boolean.FALSE.equals(managed))
							                return this.messageResourceService.getMessage("forbidden_create")
							                        .flatMap(
							                                msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN,
							                                        StringFormatter.format(msg, "Client URL"))));

						                return super.create(entity);
					                });
				        }
			        }

			        return super.create(entity);
		        });
	}

	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<ClientUrl> update(ClientUrl entity) {

		return super.update(entity);
	}

	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<ClientUrl> update(ULong key, Map<String, Object> updateFields) {

		return super.update(key, updateFields);
	}

	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<Void> delete(ULong id) {

		return this.read(id)
		        .flatMap(e -> super.delete(id));
	}

	public Mono<List<ClientUrlPattern>> readAllAsClientURLPattern() {

		Mono<List<ClientUrlPattern>> curl = cacheService.get(CACHE_NAME_CLIENT_URL, CACHE_CLIENT_URL_LIST);
		return curl.switchIfEmpty(Mono.defer(() -> this.readAllFilter(null)
				.map(ClientUrl::toClientUrlPattern)
		        .collectList()));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(Map<String, Object> fields) {

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
}
