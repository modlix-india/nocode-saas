package com.fincity.saas.commons.core.service.connection.appdata;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.model.AggregateQuery;
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

    /**
     * A grouped read: filter, group, measure, filter again, page.
     *
     * Returns FLAT rows, one per group, with group keys and measures together at
     * the top level and no nested _id. That is what lets a chart bind the result
     * without a reshaping step in between.
     */
    Mono<Page<Map<String, Object>>> aggregate(
            String clientCode, Connection conn, Storage storage, AggregateQuery query);

    /**
     * @param deleteVersion TRUE purges the row's version history. FALSE, the default on
     *                      every caller-facing surface, writes a DELETE version row
     *                      capturing the final state first, so an audited storage stops
     *                      losing the fact that the row ever existed. NULL leaves version
     *                      rows untouched, which is what internal rollback and the
     *                      builder's clear-all want: neither is a user deleting data.
     */
    Mono<Boolean> delete(String clientCode, Connection conn, Storage storage, String id, Boolean deleteVersion);

    Mono<Long> deleteByFilter(
            String clientCode, Connection conn, Storage storage, Query query, Boolean devMode, Boolean deleteVersion);

    Mono<Map<String, Object>> readVersion(String clientCode, Connection conn, Storage storage, String versionId);

    /**
     * @param includeObject FALSE drops the {@code object} snapshot from each version
     *                      row, leaving who/when/which-operation. An audit view rarely
     *                      needs the payload and the snapshot is most of the bytes.
     */
    Mono<Page<Map<String, Object>>> readPageVersion(
            String clientCode,
            Connection conn,
            Storage storage,
            String versionId,
            Query query,
            Boolean includeObject);

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

    /**
     * Seed one storage's DRAFT collection from its LIVE rows.
     *
     * Publish promotes definitions and never promotes data, so a draft surface
     * starts empty and stays that way. This is how a sandbox gets realistic rows to
     * work against. The direction is live to draft only, and it is not the inverse
     * of publish: draft rows are never copied live.
     *
     * @param replace empty the draft collection first, rather than adding to it
     * @return how many documents were written into the draft collection
     */
    Mono<Long> copyLiveToDraft(String clientCode, Connection conn, Storage storage, Boolean replace);
}
