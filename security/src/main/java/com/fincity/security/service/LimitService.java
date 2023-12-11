package com.fincity.security.service;

import java.util.function.BiFunction;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.CommonsUtil;

import reactor.core.publisher.Mono;

@Service
public class LimitService {

	@Autowired
	private LimitAccessService limitAccessService;

	@Autowired
	private LimitOwnerAccessService limitOwnerAccessService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Autowired
	@Lazy
	private ClientService clientService;

	@Autowired
	@Lazy
	private AppService appService;

	private static final String CREATE = "create";

	public Mono<Long> fetchLimits(String objectName) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(ca.getLoggedInFromClientId() == ca.getUser()
		                .getClientId()),

		        (ca, isOwner) -> this.appService.getAppByCode(ca.getUrlAppCode()),

		        (ca, isOwner, urlApp) -> this.clientService.getClientBy(ca.getUrlClientCode()),

		        (ca, isOwner, urlApp, urlClient) -> isOwner.booleanValue()
		                ? this.limitOwnerAccessService.readByAppandClientId(urlApp.getId(), urlClient.getId(),
		                        objectName)
		                : this.limitAccessService.readByAppandClientId(urlApp.getId(), urlClient.getId(), objectName),

		        (ca, isOwner, urlApp, urlClient, limitAccess) -> Mono.just(limitAccess)

		);
	}

	public Mono<ContextAuthentication> canCreate(ULong appId, ULong clientId, String objectName,
	        BiFunction<ULong, ULong, Mono<Long>> bifunction) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(ca.getLoggedInFromClientId() == ca.getUser()
		                .getClientId()),

		        (ca, isOwner) ->
				{

			        if (!ca.isSystemClient() && appId == null)
				        return this.securityMessageResourceService.throwMessage(
				                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				                SecurityMessageResourceService.MANDATORY_APP_ID);

			        return ca.isSystemClient() ? this.appService.getAppByCode(ca.getUrlAppCode())
			                : this.appService.read(appId);
		        },

		        (ca, isOwner, urlApp) -> this.clientService.getClientBy(ca.getUrlClientCode()),

		        (ca, isOwner, urlApp, urlClient) -> (isOwner.booleanValue()
		                ? this.limitOwnerAccessService.readByAppandClientId(urlApp.getId(), urlClient.getId(),
		                        objectName)
		                : this.limitAccessService.readByAppandClientId(urlApp.getId(), urlClient.getId(), objectName)),

		        (ca, isOwner, urlApp, urlClient, limitAccess) ->
				{
			        if (limitAccess == -1)
				        return Mono.just(true);

			        return bifunction
			                .apply(urlApp.getId(), CommonsUtil.nonNullValue(clientId, ULongUtil.valueOf(ca.getUser()
			                        .getClientId())))
			                .map(e -> {
			                	System.out.println(e + " received from bifunction ");
			                	System.out.println(limitAccess + " received from previous ");
			                	return e;
			                })
			                .flatMap(e -> Mono.justOrEmpty(e < limitAccess ? true : null));
		        },

		        (ca, isOwner, urlApp, urlClient, limitAccess, created) -> Mono.just(ca))

		        .switchIfEmpty(this.securityMessageResourceService.throwMessage(
		                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
		                SecurityMessageResourceService.LIMIT_MISMATCH, CREATE, objectName));

	}

}