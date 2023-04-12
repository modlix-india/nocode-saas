package com.fincity.saas.files.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.dao.FilesAccessPathDao;
import com.fincity.saas.files.dto.FilesAccessPath;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.jooq.tables.records.FilesAccessPathRecord;

import reactor.core.publisher.Mono;

@Service
public class FilesAccessPathService
        extends AbstractJOOQUpdatableDataService<FilesAccessPathRecord, ULong, FilesAccessPath, FilesAccessPathDao> {

	private static final String RESOURCE_TYPE = "resourceType";
	private static final String PATH = "path";
	private static final String WRITE_ACCESS = "writeAccess";
	private static final String ALLOW_SUB_PATH_ACCESS = "allowSubPathAccess";
	private static final String USER_ID = "userId";
	private static final String ACCESS_NAME = "accessName";

	@Autowired
	private FilesMessageResourceService msgService;

	@Autowired
	private FeignAuthenticationService securityService;

	@Override
	public Mono<FilesAccessPath> create(FilesAccessPath entity) {

		if (entity.getAccessName() == null) {
			entity.setUserId(entity.getUserId());
			entity.setAccessName(null);
		} else if (entity.getUserId() == null) {
			entity.setAccessName(entity.getAccessName());
			entity.setUserId(null);
		} else {
			msgService.throwMessage(HttpStatus.BAD_REQUEST, FilesMessageResourceService.ACCESS_ONLY_TO_ONE);
		}

		if (entity.getResourceType() == null)
			msgService.throwMessage(HttpStatus.BAD_REQUEST, FilesMessageResourceService.ACCESS_ONLY_TO_ONE);

		entity.setPath(entity.getPath() == null || entity.getPath()
		        .isBlank() ? "/"
		                : entity.getPath()
		                        .trim());

		return this.checkAccessNGetClientCode(entity.getResourceType()
		        .toString())
		        .flatMap(v -> super.create(entity.setClientCode(v)));
	}

	@Override
	public Mono<FilesAccessPath> read(ULong id) {

		return FlatMapUtil.flatMapMono(

		        () -> super.read(id),

		        e -> this.checkAccessNGetClientCode(e.getResourceType()
		                .toString(), e.getClientCode()),

		        (e, clientCode) -> Mono.just(e));
	}

	@Override
	protected Mono<FilesAccessPath> updatableEntity(FilesAccessPath entity) {

		return FlatMapUtil.flatMapMono(

		        () -> this.dao.readById(entity.getId()),

		        e -> this.checkAccessNGetClientCode(e.getResourceType()
		                .toString(), entity.getClientCode()),

		        (e, clientCode) ->
				{
			        if (entity.getAccessName() == null) {
				        e.setUserId(entity.getUserId());
				        e.setAccessName(null);
			        } else if (entity.getUserId() == null) {
				        e.setAccessName(entity.getAccessName());
				        e.setUserId(null);
			        } else {
				        msgService.throwMessage(HttpStatus.BAD_REQUEST, FilesMessageResourceService.ACCESS_ONLY_TO_ONE);
			        }

			        e.setAllowSubPathAccess(entity.isAllowSubPathAccess());
			        e.setPath(entity.getPath() == null || entity.getPath()
			                .isBlank() ? "/"
			                        : entity.getPath()
			                                .trim());
			        e.setWriteAccess(entity.isWriteAccess());

			        return Mono.just(e);
		        });
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong id, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

		        () -> this.dao.readById(id),

		        e -> this.checkAccessNGetClientCode(e.getResourceType()
		                .toString(), e.getClientCode()),

		        (e, clientCode) ->
				{

			        Map<String, Object> map = new HashMap<>();

			        if (!StringUtil.safeIsBlank(fields.get(ACCESS_NAME))) {
				        if (!StringUtil.safeIsBlank(e.getUserId()))
					        map.put(USER_ID, null);
				        map.put(ACCESS_NAME, fields.get(ACCESS_NAME));
			        } else if (!StringUtil.safeIsBlank(fields.get(USER_ID))) {
				        if (!StringUtil.safeIsBlank(e.getUserId()))
					        map.put(ACCESS_NAME, null);
				        map.put(USER_ID, fields.get(USER_ID));
			        }

			        if (fields.containsKey(ALLOW_SUB_PATH_ACCESS))
				        map.put(ALLOW_SUB_PATH_ACCESS, BooleanUtil.safeValueOf(fields.get(ALLOW_SUB_PATH_ACCESS)));

			        if (fields.containsKey(WRITE_ACCESS))
				        map.put(WRITE_ACCESS, BooleanUtil.safeValueOf(fields.get(WRITE_ACCESS)));

			        if (!StringUtil.safeIsBlank(fields.get(PATH)))
				        map.put(PATH, fields.get(PATH));

			        return Mono.just(map);
		        });
	}

	@Override
	public Mono<Page<FilesAccessPath>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return FlatMapUtil.flatMapMono(

		        () -> condition.findConditionWithField(RESOURCE_TYPE)
		                .collectList(),

		        cs -> SecurityContextUtil.getUsersContextAuthentication(),

		        (cs, ca) ->
				{
			        if (!ca.isSystemClient() && !ca.getLoggedInFromClientId()
			                .equals(ca.getUser()
			                        .getClientId())) {

				        return msgService.throwMessage(HttpStatus.FORBIDDEN,
				                FilesMessageResourceService.FORBIDDEN_PERMISSION, "");
			        }

			        boolean hasStatic = SecurityContextUtil.hasAuthority(
			                this.getAuthority(FilesAccessPathResourceType.STATIC.toString()), ca.getAuthorities());
			        boolean hasSecured = SecurityContextUtil.hasAuthority(
			                this.getAuthority(FilesAccessPathResourceType.SECURED.toString()), ca.getAuthorities());

			        if (cs.isEmpty()) {

				        if (!hasSecured && !hasStatic)
					        return msgService.throwMessage(HttpStatus.FORBIDDEN,
					                FilesMessageResourceService.FORBIDDEN_PERMISSION,
					                "STATIC Files PATH / SECURED Files PATH");

				        if (hasSecured && hasStatic)
					        return Mono.just(condition);

				        if (hasSecured)
					        return Mono
					                .just(new ComplexCondition()
					                        .setConditions(List.of(condition,
					                                new FilterCondition().setField(RESOURCE_TYPE)
					                                        .setValue(FilesAccessPathResourceType.SECURED.toString())))
					                        .setOperator(ComplexConditionOperator.AND));

				        return Mono.just(new ComplexCondition()
				                .setConditions(List.of(condition, new FilterCondition().setField(RESOURCE_TYPE)
				                        .setValue(FilesAccessPathResourceType.STATIC.toString())))
				                .setOperator(ComplexConditionOperator.AND));
			        } else {

				        List<String> list = cs.stream()
				                .filter(FilterCondition.class::isInstance)
				                .map(e -> ((FilterCondition) e).getValue())
				                .map(Object::toString)
				                .distinct()
				                .toList();

				        for (String rtype : list) {

					        if (rtype.contains(FilesAccessPathResourceType.STATIC.toString()) && !hasStatic) {
						        return msgService.throwMessage(HttpStatus.FORBIDDEN,
						                FilesMessageResourceService.FORBIDDEN_PERMISSION, "STATIC Files PATH");
					        }

					        if (rtype.contains(FilesAccessPathResourceType.SECURED.toString()) && !hasSecured) {
						        return msgService.throwMessage(HttpStatus.FORBIDDEN,
						                FilesMessageResourceService.FORBIDDEN_PERMISSION, "SECURED Files PATH");
					        }
				        }
			        }

			        return Mono.just(condition);
		        },

		        (cs, ca, newCondition) -> super.readPageFilter(pageable,
		                new ComplexCondition()
		                        .setConditions(List.of(newCondition, new FilterCondition().setField("clientCode")
		                                .setValue(ca.getLoggedInFromClientCode())))
		                        .setOperator(ComplexConditionOperator.AND)));
	}

	public Mono<String> checkAccessNGetClientCode(String resourceType) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.checkAccessNGetClientCode(resourceType, ca.getLoggedInFromClientCode()));
	}

	public Mono<String> checkAccessNGetClientCode(String resourceType, String clientCode) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{
			        if (ca.isSystemClient() || ca.getLoggedInFromClientId()
			                .equals(ca.getUser()
			                        .getClientId()))
				        return Mono.just(true);

			        return securityService.isUserBeingManaged(ca.getUser()
			                .getId(), clientCode);
		        },

		        (ca, managed) ->
				{

			        if (!managed.booleanValue()
			                || !SecurityContextUtil.hasAuthority(this.getAuthority(resourceType), ca.getAuthorities())) {
				        return msgService.throwMessage(HttpStatus.FORBIDDEN,
				                FilesMessageResourceService.FORBIDDEN_PERMISSION, this.getAuthority(resourceType));
			        }

			        return Mono.just(ca.getLoggedInFromClientCode());
		        }

		);

	}

	private String getAuthority(String resourceType) {
		return "Authorities." + resourceType + "_Files_PATH";
	}

	@Override
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

		        () -> this.dao.readById(id),

		        e -> this.checkAccessNGetClientCode(e.getResourceType()
		                .toString(), e.getClientCode()),

		        (e, clientCode) -> super.delete(id));
	}

	public Mono<Boolean> hasReadAccess(String actualPath, String clientCode, FilesAccessPathResourceType resourceType) {

		String path = actualPath.endsWith("/") ? actualPath.substring(0, actualPath.length() - 1) : actualPath;

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> ca.isSystemClient() ? Mono.just(true)
		                : this.securityService.isBeingManaged(ca.getClientCode(), clientCode),

		        (ca, managed) ->
				{
			        if (!managed.booleanValue())
				        return Mono.just(false);

			        return this.dao.hasPathReadAccess(path, ULong.valueOf(ca.getUser()
			                .getId()), clientCode, resourceType, ca.getAuthorities()
			                        .stream()
			                        .map(GrantedAuthority::getAuthority)
			                        .toList());
		        })
		        .defaultIfEmpty(false);

	}

	public Mono<Boolean> hasWriteAccess(String actualPath, String clientCode,
	        FilesAccessPathResourceType resourceType) {

		String path = actualPath.endsWith("/") ? actualPath.substring(0, actualPath.length() - 1) : actualPath;

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> ca.isSystemClient() ? Mono.just(true)
		                : this.securityService.isBeingManaged(ca.getClientCode(), clientCode),

		        (ca, managed) ->
				{
			        if (!managed.booleanValue())
				        return Mono.just(false);

			        return this.dao.hasPathWriteAccess(path, ULong.valueOf(ca.getUser()
			                .getId()), clientCode, resourceType, ca.getAuthorities()
			                        .stream()
			                        .map(GrantedAuthority::getAuthority)
			                        .toList());
		        })
		        .defaultIfEmpty(false);
	}
}
