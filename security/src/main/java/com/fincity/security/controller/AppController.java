package com.fincity.security.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;
import com.fincity.security.model.ApplicationAccessPackageOrRoleRequest;
import com.fincity.security.model.ApplicationAccessRequest;
import com.fincity.security.service.AppService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@RestController
@RequestMapping("api/security/applications")
public class AppController
		extends AbstractJOOQUpdatableDataController<SecurityAppRecord, ULong, App, AppDAO, AppService> {

	@Value("${security.appCodeSuffix:}")
	private String appCodeSuffix;

	@Value("${security.resourceCacheAge:604800}")
	private int cacheAge;

	@GetMapping("/applyAppCodeSuffix")
	public Mono<ResponseEntity<String>> applyAppCodeSuffix(@RequestParam String appCode) {
		return Mono.just(ResponseEntity.ok().header("ETag", "W/" + appCode)
				.header("Cache-Control", "max-age: " + cacheAge)
				.header("x-frame-options", "SAMEORIGIN")
				.header("X-Frame-Options", "SAMEORIGIN").body(appCode + appCodeSuffix));
	}

	@GetMapping("/internal/hasReadAccess")
	public Mono<ResponseEntity<Boolean>> hasReadAccess(@RequestParam String appCode, @RequestParam String clientCode) {

		return this.service.hasReadAccess(appCode, clientCode)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/internal/hasDeleteAccess")
	public Mono<ResponseEntity<Boolean>> hasDeleteAccess(@RequestParam String appCode) {

		return this.service.hasDeleteAccess(appCode)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/internal/appInheritance")
	public Mono<ResponseEntity<List<String>>> appInheritance(@RequestParam String appCode,
			@RequestParam String urlClientCode, @RequestParam String clientCode) {

		return this.service.appInheritance(appCode, urlClientCode, clientCode)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/internal/hasWriteAccess")
	public Mono<ResponseEntity<Boolean>> hasWriteAccess(@RequestParam String appCode, @RequestParam String clientCode) {

		return this.service.hasWriteAccess(appCode, clientCode)
				.map(ResponseEntity::ok);
	}

	@DeleteMapping("/everything/{id}")
	public Mono<ResponseEntity<Boolean>> deleteByAppId(@PathVariable(PATH_VARIABLE_ID) final ULong id,
			@RequestParam(required = false) final Boolean forceDelete) {

		return this.service.deleteEverything(id, BooleanUtil.safeValueOf(forceDelete))
				.map(ResponseEntity::ok);
	}

	@GetMapping("/internal/appCode/{appCode}")
	public Mono<ResponseEntity<App>> getAppCode(@PathVariable("appCode") final String appCode) {

		return this.service.getAppByCode(appCode)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/{id}/access")
	public Mono<ResponseEntity<Boolean>> addClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
			@RequestBody final ApplicationAccessRequest request) {
		return this.service.addClientAccess(appId, request.getClientId(), request.isWriteAccess())
				.map(ResponseEntity::ok);
	}

	@PatchMapping("/{id}/access")
	public Mono<ResponseEntity<Boolean>> updateClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
			@RequestBody final ApplicationAccessRequest request) {
		return this.service.updateClientAccess(request.getId(), request.isWriteAccess())
				.map(ResponseEntity::ok);
	}

	@DeleteMapping("/{id}/access")
	public Mono<ResponseEntity<Boolean>> removeClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
			@RequestParam final ULong accessId) {
		return this.service.removeClient(appId, accessId)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/{id}/packageAccess")
	public Mono<ResponseEntity<Boolean>> addPackageAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
			@RequestBody final ApplicationAccessPackageOrRoleRequest appRequest) {

		return this.service.addPackageAccess(appId, appRequest.getClientId(), appRequest.getPackageId())
				.map(ResponseEntity::ok);
	}

	@GetMapping("/getPackages/{appCode}")
	public Mono<Object> fetchPackagesList(@PathVariable String appCode,
			@RequestParam(required = false) ULong clientId) {

		return this.service.getPackagesAssignedToApp(appCode, clientId)
				.map(ResponseEntity::ok);
	}

	@DeleteMapping("/{id}/packageAccess")
	public Mono<ResponseEntity<Boolean>> removePackageAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
			@RequestParam final ULong clientId, @RequestParam final ULong packageId) {

		return this.service.removePackageAccess(appId, clientId, packageId)
				.map(ResponseEntity::ok);

	}

	@PostMapping("/{id}/roleAccess")
	public Mono<ResponseEntity<Boolean>> addRoleAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
			@RequestBody final ApplicationAccessPackageOrRoleRequest roleRequest) {

		return this.service.addRoleAccess(appId, roleRequest.getClientId(), roleRequest.getRoleId())
				.map(ResponseEntity::ok);
	}

	@GetMapping("/getRoles/{appCode}")
	public Mono<Object> fetchRolesList(@PathVariable final String appCode,
			@RequestParam(required = false) ULong clientId) {

		return this.service.getRolesAssignedToApp(appCode, clientId)
				.map(ResponseEntity::ok);
	}

	@DeleteMapping("/{id}/roleAccess")
	public Mono<ResponseEntity<Boolean>> removeRoleAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
			@RequestParam final ULong clientId, @RequestParam final ULong roleId) {

		return this.service.removeRoleAccess(appId, clientId, roleId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/clients/{appCode}")
	public Mono<ResponseEntity<List<Client>>> getAppClients(@PathVariable final String appCode,
			@RequestParam(required = false) Boolean onlyWriteAccess) {
		return this.service.getAppClients(appCode, onlyWriteAccess)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/property")
	public Mono<ResponseEntity<List<AppProperty>>> getProperty(@RequestParam ULong clientId,
			@RequestParam(required = false) ULong appId, @RequestParam(required = false) String appCode,
			@RequestParam(required = false) String propName) {

		return this.service.getProperties(clientId, appId, appCode, propName)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/property")
	public Mono<ResponseEntity<Boolean>> updateProperty(@RequestBody AppProperty property) {

		return this.service.updateProperty(property)
				.map(ResponseEntity::ok);
	}

	@DeleteMapping("/property")
	public Mono<ResponseEntity<Boolean>> deleteProperty(@RequestParam ULong clientId,
			@RequestParam ULong appId,
			@RequestParam String name) {

		return this.service.deleteProperty(clientId, appId, name)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/findBaseClientCode/{applicationCode}")
	public Mono<ResponseEntity<Tuple2<String, Boolean>>> findBaseClientCodeForOverride(
			@PathVariable("") String applicationCode) {
		return this.service.findBaseClientCodeForOverride(applicationCode)
				.map(ResponseEntity::ok);
	}
}
