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

    /**
     * Appended to the app's database name on the draft surface, giving
     * {@code <clientCode>_<appCode>_draft}. Collection names are unchanged, so a
     * storage keeps the same physical name on both surfaces.
     */
    String DRAFT_DB_SUFFIX = "_draft";

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

    /**
     * Drop a storage's DRAFT collection, whatever surface the caller is on.
     *
     * Draft rows are sandbox data, so unlike live rows they are safe to discard
     * when the definition that gave them meaning goes away. deleteStorage only ever
     * touches the current surface, so this exists to reach the other one.
     */
    Mono<Boolean> dropDraftStorage(String clientCode, Connection conn, Storage storage);

    /**
     * Drop an app's entire draft database.
     *
     * Deliberately does not touch the live database: orphaning live app data on app
     * deletion is long-standing behaviour and changing it is a separate decision
     * with real consequences.
     */
    Mono<Boolean> dropDraftDatabase(Connection conn, String appCode, String clientCode);
}
