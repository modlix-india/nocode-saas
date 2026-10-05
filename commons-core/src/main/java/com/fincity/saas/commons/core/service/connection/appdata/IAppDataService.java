package com.fincity.saas.commons.core.service.connection.appdata;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Query;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IAppDataService {
    String CACHE_SUFFIX_FOR_INDEX_CREATION = "_index_creation";

    /**
     * Memoises "this tenant's table has been created", for backends that have tables.
     *
     * Separate from the index cache rather than sharing it. They are evicted at the
     * same two moments today, and the moment that stops being true - a backend that
     * rebuilds indexes without touching the table, or the reverse - one of them would
     * silently start lying, and the symptom is a write against a table that is not
     * there.
     */
    String CACHE_SUFFIX_FOR_TABLE_CREATION = "_table_creation";

    /**
     * The foreign keys a tenant's table actually has.
     *
     * Separate from the table cache because the two are invalidated by different
     * things: a key can appear without the table changing, when orphan rows that
     * blocked it are cleared and the next publish installs it.
     */
    String CACHE_SUFFIX_FOR_FOREIGN_KEYS = "_foreign_keys";

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

    /**
     * Whether the database IS enforcing this relation, so the service must not.
     *
     * The deciding question for every delete, and it has to be asked of the backend
     * rather than answered in the service. A relation MySQL has turned into a real
     * foreign key is already protected inside the delete statement, against rows no
     * other transaction can slip past; running the service loop over the top would
     * count the same children twice and make the behaviour depend on which of the
     * two noticed first.
     *
     * Reactive, and that is the whole point of the signature. The obvious version of
     * this returns a plain boolean from the definition - can this relation be a
     * foreign key? - and the answer is a lie whenever the key could not actually be
     * installed. Orphan rows block an ADD CONSTRAINT, and so does a target storage
     * that has stopped resolving for a client; in both cases the definition still
     * says "yes, a key" while the table has none, and the service would stand down
     * for a constraint nobody is enforcing. Asking the table costs one catalogue
     * read per storage per tenant, cached, and only for relations that declare a
     * constraint at all - which today is none of them.
     *
     * Mongo has no foreign keys, so it answers no to everything and keeps the loop.
     * That is the whole reason the loop exists.
     *
     * @param child    the storage whose relation declares the constraint
     * @param field    the field holding the reference, which is the relation's key
     * @param relation the relation pointing at the storage being deleted
     */
    default Mono<Boolean> enforcesRelationConstraint(
            String clientCode, Connection conn, Storage child, String field, StorageRelation relation) {
        return Mono.just(Boolean.FALSE);
    }

    /**
     * How many rows of {@code child} point at {@code id} through {@code field}.
     *
     * A backend method rather than a {@link Query} built by the caller because the
     * two store a TO_MANY relation differently enough that no one filter describes
     * both: Mongo keeps an array and matches an element with plain equality, while
     * MySQL keeps a JSON document that equality would never match. Expressed as a
     * filter it would silently find nothing on MySQL, which for a RESTRICT means
     * allowing exactly the delete it exists to refuse.
     */
    Mono<Long> countReferencing(
            String clientCode, Connection conn, Storage child, String field, boolean many, String id);

    /** The ids of those rows, for a cascade. Capped, because a cascade is not a bulk job. */
    Mono<List<String>> idsReferencing(
            String clientCode, Connection conn, Storage child, String field, boolean many, String id, int limit);

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
