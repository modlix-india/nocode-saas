package com.fincity.security.service;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.model.ClientUrlPattern;
import com.fincity.security.model.ClientUrlPattern.Protocol;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class ClientService extends AbstractUpdatableDataService<SecurityClientRecord, ULong, Client, ClientDAO> {

	private static final String CACHE_NAME_CLIENT_RELATION = "clientRelation";
	private static final String CACHE_NAME_CLIENT_PWD_POLICY = "clientPasswordPolicy";
	private static final String CACHE_NAME_CLIENT_TYPE = "clientType";
	private static final String CACHE_NAME_CLIENT_URL = "clientClientURL";

	private static final String CACHE_CLIENT_URI = "uri";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private ClientUrlService clientUrlService;

	public Mono<ULong> getClientId(ServerHttpRequest request) {

		final URI uri = request.getURI();

		Mono<String> key = cacheService.makeKey(CACHE_CLIENT_URI, uri.getScheme(), uri.getHost(), ":", uri.getPort());

		Mono<ULong> clientId = key.flatMap(e -> cacheService.get(CACHE_NAME_CLIENT_URL, e));

		return clientId.switchIfEmpty(clientUrlService.readAllAsClientURLPattern()
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
		        .map(ClientUrlPattern::getClientId)
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

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {
		return this.dao.isBeingManagedBy(managingClientId, clientId);
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
	
	@PreAuthorize("hasPermission('Client CREATE')")
	@Override
	public Mono<Client> create(Client entity) {
		
		Mono<Client> client = super.create(entity);
		
//		client.and
		
		return client;
	}
	
	@PreAuthorize("hasPermission('Client READ')")
	@Override
	public Mono<Client> read(ULong id) {
		return super.read(id);
	}
	
	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<Client> update(Client entity) {
		return super.update(entity);
	}
	
	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<Client> update(ULong key, Map<String, Object> fields) {
		return super.update(key, fields);
	}

	@PreAuthorize("hasPermission('Client UPDATE')")
	@Override
	public Mono<Void> delete(ULong id) {
		return super.delete(id);
	}

	@Override
	protected Mono<Client> updatableEntity(Client entity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(Map<String, Object> fields) {
		// TODO Auto-generated method stub
		return null;
	}
}
