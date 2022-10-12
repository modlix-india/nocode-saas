package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.PackageDAO;
import com.fincity.security.dto.Package;
import com.fincity.security.jooq.tables.records.SecurityPackageRecord;
import com.fincity.security.service.PackageService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/packages")
public class PackageController
        extends AbstractJOOQDataController<SecurityPackageRecord, ULong, Package, PackageDAO, PackageService> {

	@Autowired
	private PackageService packageService;

	@GetMapping("/{packageId}/removeRole/{roleId}")
	public Mono<ResponseEntity<Boolean>> removeRole(@PathVariable ULong packageId, @PathVariable ULong roleId) {

		return packageService.removeRoleFromPackage(packageId, roleId)
		        .map(ResponseEntity::ok);

	}
}
