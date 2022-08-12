package com.fincity.security.service;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;
import com.fincity.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

@Service
public class ClientUrlService
        extends AbstractUpdatableDataService<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO> {

	@Autowired
	private ClientService clientService;

	@Autowired
	private CacheService cacheService;

	private static final String CACHE_NAME_CLIENT_URL = "clientUrl";
	private static final String CACHE_CLIENT_URL_LIST = "list";

	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<ClientUrl> read(ULong id) {

		return super.read(id).map(cu -> {

			SecurityContextUtil.getUsersClientId()
				.map(ULong::valueOf)
				.
				.flatMap(clientService::getPotentialClientList)
				.filter(e -> e.contains(cu.getId()))
				.
			return cu;
		});
	}

	public Mono<List<ClientUrl>> readAll() {

		Mono<List<ClientUrl>> curl = cacheService.get(CACHE_NAME_CLIENT_URL, CACHE_CLIENT_URL_LIST);
		return curl.switchIfEmpty(Mono.defer(() -> this.readAllFilter(null)
		        .collectList()));
	}
}
