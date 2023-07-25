package com.fincity.saas.core.service.connection.appdata;

import java.util.Map;

import org.springframework.data.domain.Page;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.model.DataObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IAppDataService {

	public Mono<Map<String, Object>> create(Connection conn, Storage storage, DataObject dataObject);

	public Mono<Map<String, Object>> update(Connection conn, Storage storage, DataObject dataObject, Boolean override);

	public Mono<Map<String, Object>> read(Connection conn, Storage storage, String id);

	public Mono<Page<Map<String, Object>>> readPage(Connection conn, Storage storage, Query query);

	public Flux<Map<String, Object>> readPageAsFlux(Connection conn, Storage storage, Query query);

	public Mono<Boolean> delete(Connection conn, Storage storage, String id);

	public Mono<Map<String, Object>> readVersion(Connection conn, Storage storage, String versionId);

	public Mono<Page<Map<String, Object>>> readPageVersion(Connection conn, Storage storage, String versionId,
	        Query query);

}
