package com.fincity.security.service;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.ClientURLPattern;
import com.fincity.security.model.ClientURLPattern.Protocol;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class ClientService extends AbstractUpdatableDataService<SecurityClientRecord, ULong, Client, ClientDAO> {

	private static final String CACHE_NAME_CLIENT_RELATION = "clientRelation";
	private static final String CACHE_NAME_CLIENT_PWD_POLICY = "clientPasswordPolicy";
	private static final String CACHE_NAME_CLIENT_TYPE = "clientType";

	private static final String CACHE_CLIENT_URI = "uri";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private ClientUrlService clientUrlService;
//
//	public void addClientURLPattern(ULong clientId, String urlPattern) {
//
//		cacheService.evictAll(CACHE_NAME_CLIENT_URL)
//		        .and(this.dao.addClientURLPattern(clientId, urlPattern))
//		        .subscribe();
//	}
//
//	public Mono<List<ClientURLPattern>> clientURLPatterns() {
//
//		Mono<List<ClientURLPattern>> list = cacheService.get(CACHE_NAME_CLIENT_URL, CACHE_CLIENT_URL_LIST);
//
//		return list.switchIfEmpty(this.dao.clientURLPatterns()
//		        .flatMap(e -> cacheService.put(CACHE_NAME_CLIENT_URL, e, CACHE_CLIENT_URL_LIST)));
//	}

	public Mono<ULong> getClientId(ServerHttpRequest request) {

		final URI uri = request.getURI();

		Mono<String> key = cacheService.makeKey(CACHE_CLIENT_URI, uri.getScheme(), uri.getHost(), ":", uri.getPort());

		Mono<ULong> clientId = key.flatMap(e -> cacheService.get(CACHE_NAME_CLIENT_URL, e));

		return clientId.switchIfEmpty(this.dao.clientURLPatterns()
		        .flatMapIterable(e -> e)
		        .filter(e ->
				{

			        Tuple3<Protocol, String, Integer> tuple = e.getHostnPort();

			        String scheme = uri.getScheme()
			                .toLowerCase();

			        if (!tuple.getT2()
			                .equals(uri.getHost()
			                        .toLowerCase()))
				        return false;

			        int checkPort = -1;

			        if (tuple.getT1() == Protocol.HTTPS) {

				        if (!scheme.startsWith("https:"))
					        return false;

				        checkPort = 443;
			        } else if (tuple.getT1() == Protocol.HTTP) {

				        if (!scheme.startsWith("http:"))
					        return false;

				        checkPort = 80;
			        }

			        if (tuple.getT3() != -1)
				        checkPort = tuple.getT3();

			        return checkPort == -1 || uri.getPort() == checkPort;
		        })
		        .map(ClientURLPattern::getClientId)
		        .defaultIfEmpty(ULong.valueOf(1l))
		        .take(1)
		        .single()
		        .flatMap(e -> key.flatMap(k -> cacheService.put(CACHE_NAME_CLIENT_URL, e, k))));
	}

	public Mono<Set<ULong>> getPotentialClientList(ServerHttpRequest request) {

		return this.getClientId(request)
		        .flatMap(this::getPotentialClientList);
	}

	public Mono<Set<ULong>> getPotentialClientList(ULong k) {

		Mono<Set<ULong>> clientList = cacheService.get(CACHE_NAME_CLIENT_RELATION, k);

		return clientList.switchIfEmpty(Mono.defer(() -> this.dao.findManagedClientList(k)
		        .flatMap(v -> cacheService.put(CACHE_NAME_CLIENT_RELATION, v, k))));
	}

	public Mono<ClientPasswordPolicy> getClientPasswordPolicy(ULong clientId) {

		Mono<ClientPasswordPolicy> policy = cacheService.get(CACHE_NAME_CLIENT_PWD_POLICY, clientId);

		return policy.switchIfEmpty(Mono.defer(() -> this.dao.getClientPasswordPolicy(clientId)
		        .switchIfEmpty(Mono.just(new ClientPasswordPolicy()))
		        .flatMap(v -> cacheService.put(CACHE_NAME_CLIENT_PWD_POLICY, v, clientId))));
	}

	public Mono<String> getClientType(ULong id) {

		Mono<String> clientType = cacheService.get(CACHE_NAME_CLIENT_TYPE, id);

		return clientType.switchIfEmpty(Mono.defer(() -> this.dao.getClientType(id)
		        .flatMap(v -> cacheService.put(CACHE_NAME_CLIENT_TYPE, v, id))));
	}

}
