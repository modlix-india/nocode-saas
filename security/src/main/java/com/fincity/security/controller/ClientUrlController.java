package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;
import com.fincity.security.service.ClientUrlService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/clienturls")
public class ClientUrlController
        extends AbstractJOOQDataController<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO, ClientUrlService> {

	public Mono<ResponseEntity<Page<ClientUrl>>> getUrlsOfApp(Pageable page,
	        @RequestParam(required = true) String appCode) {

		return this.service.getUrlsBasedOnApp(page, appCode)
		        .map(ResponseEntity::ok);
	}
}
