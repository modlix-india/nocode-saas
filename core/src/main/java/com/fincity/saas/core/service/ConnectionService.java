package com.fincity.saas.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoUpdatableDataService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.repository.ConnectionRepository;

import reactor.core.publisher.Mono;

@Service
public class ConnectionService extends AbstractMongoUpdatableDataService<String, Connection, ConnectionRepository> {

	@Autowired
	protected FeignAuthenticationService securityService;

	@Autowired
	protected CoreMessageResourceService messageResourceService;

	public ConnectionService() {
		super(Connection.class);
	}

	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Connection_CREATE')")
	@Override
	public Mono<Connection> create(Connection entity) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{

			        if (entity.getClientCode() == null)
				        entity.setClientCode(ca.getClientCode());

			        if (ca.isSystemClient() || ca.getClientCode()
			                .equals(entity.getClientCode()))
				        return Mono.just(true);

			        return this.securityService.isBeingManaged(ca.getClientCode(), entity.getClientCode());
		        },

		        (ca, access) ->
				{
			        if (!access.booleanValue())
				        return Mono.empty();

			        return super.create(entity);
		        })
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                CoreMessageResourceService.FORBIDDEN_CREATE, "Connection")));
	}
	
	@PreAuthorize("hasAuthority('Authorities.APPBUILDER.Connection_UPDATE')")
	@Override
	public Mono<Connection> update(Connection entity) {
	
		return super.update(entity);
	}

	@Override
	protected Mono<String> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ContextAuthentication::getUser)
		        .map(ContextUser::getId)
		        .map(Object::toString);
	}

//	@Override
//	protected Mono<Connection> updatableEntity(Connection entity) {
//		
//		return FlatMapUtil.flatMapMono(
//				() -> 
//				);
//	}
}
