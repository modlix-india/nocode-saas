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

	String CACHE_SUFFIX_FOR_INDEX_CREATION = "_index_creation";

	Mono<Map<String, Object>> create(Connection conn, Storage storage, DataObject dataObject);

	Mono<Map<String, Object>> update(Connection conn, Storage storage, DataObject dataObject, Boolean override);

	Mono<Map<String, Object>> read(Connection conn, Storage storage, String id);

	Mono<Page<Map<String, Object>>> readPage(Connection conn, Storage storage, Query query);

	Flux<Map<String, Object>> readPageAsFlux(Connection conn, Storage storage, Query query);

	Mono<Boolean> delete(Connection conn, Storage storage, String id, Boolean deleteVersion);

	Mono<Long> deleteByFilter(Connection conn, Storage storage, Query query, Boolean devMode, Boolean deleteVersion);

	Mono<Map<String, Object>> readVersion(Connection conn, Storage storage, String versionId);

	Mono<Page<Map<String, Object>>> readPageVersion(Connection conn, Storage storage, String versionId, Query query);

	Mono<Boolean> checkifExists(Connection conn, Storage storage, String id);

	Mono<Boolean> deleteStorage(Connection conn, Storage storage);
}
