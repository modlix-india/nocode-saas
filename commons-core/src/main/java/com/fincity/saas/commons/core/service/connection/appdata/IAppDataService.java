package com.fincity.saas.commons.core.service.connection.appdata;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.model.Query;

import java.util.Map;

import org.springframework.data.domain.Page;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IAppDataService {
    String CACHE_SUFFIX_FOR_INDEX_CREATION = "_index_creation";

    Mono<Map<String, Object>> create(String clientCode, Connection conn, Storage storage, DataObject dataObject);

    Mono<Map<String, Object>> update(String clientCode, Connection conn, Storage storage, DataObject dataObject, Boolean override);

    Mono<Map<String, Object>> read(String clientCode, Connection conn, Storage storage, String id);

    Mono<Page<Map<String, Object>>> readPage(String clientCode, Connection conn, Storage storage, Query query);

    Flux<Map<String, Object>> readPageAsFlux(String clientCode, Connection conn, Storage storage, Query query);

    Mono<Boolean> delete(String clientCode, Connection conn, Storage storage, String id);

    Mono<Long> deleteByFilter(String clientCode, Connection conn, Storage storage, Query query, Boolean devMode);

    Mono<Map<String, Object>> readVersion(String clientCode, Connection conn, Storage storage, String versionId);

    Mono<Page<Map<String, Object>>> readPageVersion(String clientCode, Connection conn, Storage storage, String versionId, Query query);

    Mono<Boolean> checkIfExists(String clientCode, Connection conn, Storage storage, String id);

    Mono<Boolean> deleteStorage(String clientCode, Connection conn, Storage storage);
}
