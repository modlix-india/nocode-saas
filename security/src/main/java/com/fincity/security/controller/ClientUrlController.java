package com.fincity.security.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.GetMapping;
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

	@GetMapping("/fetchUrls")
	public Mono<List<String>> getUrlsOfApp(@RequestParam(required = true) String appCode,
			@RequestParam(required = false) String suffix) {
		
		return this.service.getUrlsBasedOnApp(appCode, suffix);
	}

}
