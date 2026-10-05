package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.springframework.data.domain.Sort;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.document.Storage.StorageIndex;
import com.fincity.saas.commons.core.document.Storage.StorageIndexField;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.StorageFieldNames;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The indexes a storage's table should have.
 *
 * Mongo has always honoured {@code storage.indexes} and {@code textIndexFields}.
 * This backend referenced neither, so every table had exactly one index - the
 * primary key it is created with - and every filter on every other column was a full
 * scan. {@code TEXT_SEARCH} did not even degrade: it returned a 501 that said a
 * FULLTEXT index was needed and that storage definitions do not create one.
 *
 * The declared set is authoritative, which is how Mongo treats it: an index the
 * definition no longer names is dropped. Two things are exempt, and both for the
 * same reason - dropping them fails the statement rather than tidying anything. The
 * primary key is not an index anyone declared, and an index backing a foreign key
 * cannot be removed while the constraint exists.
 */
public final class MySQLIndexes {

    /** Prefix for the index this backend adds to a relation column nobody declared. */
    static final String RELATION_PREFIX = "rel_";

    /** The one FULLTEXT index, matching Mongo's one text index per collection. */
    static final String FULLTEXT_NAME = "ft_all";

    private static final int MAX_NAME = 64;

    private MySQLIndexes() {
    }

    /**
     * One index, as both the definition and MySQL describe it.
     *
     * @param name       the index name, which is the key in {@code storage.indexes}
     * @param columns    in order; order is what makes a compound index usable or not
     * @param directions one per column, or empty when every column is ascending
     * @param unique     a constraint, not a hint
     * @param fullText   built from {@code textIndexFields} rather than from an index entry
     */
    public record Index(
            String name, List<String> columns, List<Sort.Direction> directions, boolean unique, boolean fullText) {

        public String ddl(String db, String table) {

            StringBuilder sb = new StringBuilder("CREATE ");
            if (this.fullText) sb.append("FULLTEXT ");
            else if (this.unique) sb.append("UNIQUE ");

            sb.append("INDEX `")
                    .append(this.name)
                    .append("` ON `")
                    .append(db)
                    .append("`.`")
                    .append(table)
                    .append("` (");

            for (int i = 0; i < this.columns.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append('`').append(this.columns.get(i)).append('`');
                // Not on a FULLTEXT index, where MySQL does not accept one.
                if (!this.fullText && i < this.directions.size())
                    sb.append(this.directions.get(i) == Sort.Direction.DESC ? " DESC" : " ASC");
            }

            return sb.append(')').toString();
        }

        /** Same index, ignoring the name, so a rename is not read as a change. */
        public boolean sameAs(Index other) {
            return other != null
                    && this.columns.equals(other.columns)
                    && this.unique == other.unique
                    && this.fullText == other.fullText
                    && this.directions.equals(other.directions);
        }
    }

    /**
     * What the definition asks for, filtered to fields that exist.
     *
     * An index naming a field the storage no longer has is dropped rather than
     * refused. The definition is overridable, so a client can legitimately resolve a
     * schema without a field its base indexes, and failing there would make that one
     * tenant's table impossible to create over a missing index.
     */
    public static List<Index> desired(Storage storage, Set<String> columns) {

        List<Index> out = new ArrayList<>();
        if (storage == null) return out;

        if (storage.getIndexes() != null)
            storage.getIndexes().forEach((name, index) -> {
                Index built = declared(name, index, columns);
                if (built != null) out.add(built);
            });

        Index text = fullText(storage.getTextIndexFields(), columns);
        if (text != null) out.add(text);

        out.addAll(relationIndexes(storage, columns, out));

        return out;
    }

    private static Index declared(String name, StorageIndex index, Set<String> columns) {

        if (index == null || index.getFields() == null || index.getFields().isEmpty()) return null;
        if (!StorageFieldNames.valid(name) || name.length() > MAX_NAME) return null;

        List<String> cols = new ArrayList<>();
        List<Sort.Direction> directions = new ArrayList<>();

        for (StorageIndexField field : index.getFields()) {
            if (field == null || !columns.contains(field.getFieldName())) return null;
            cols.add(field.getFieldName());
            directions.add(field.getDirection() == null ? Sort.Direction.ASC : field.getDirection());
        }

        return new Index(name, cols, directions, index.isUnique(), false);
    }

    private static Index fullText(List<String> textFields, Set<String> columns) {

        if (textFields == null || textFields.isEmpty()) return null;

        List<String> cols = textFields.stream().filter(columns::contains).toList();
        if (cols.isEmpty()) return null;

        return new Index(FULLTEXT_NAME, cols, List.of(), false, true);
    }

    /**
     * An index on each TO_ONE relation column, unless something already covers it.
     *
     * The join direction is a primary key lookup and was always fast. The reverse -
     * every order for this customer - is not, and neither is the RESTRICT check,
     * which counts exactly this column on every delete of a referenced row.
     *
     * TO_MANY is left out because it is a JSON array, and MySQL cannot index into
     * one without a generated column per element.
     */
    private static List<Index> relationIndexes(Storage storage, Set<String> columns, List<Index> already) {

        List<Index> out = new ArrayList<>();
        if (storage.getRelations() == null) return out;

        Set<String> covered = new LinkedHashSet<>();
        // An index whose FIRST column is this one already serves it.
        for (Index index : already) if (!index.columns().isEmpty()) covered.add(index.columns().getFirst());

        storage.getRelations().forEach((field, relation) -> {
            if (relation == null || relation.getRelationType() != StorageRelationType.TO_ONE) return;
            if (!columns.contains(field) || covered.contains(field)) return;
            if (!StorageFieldNames.valid(field)) return;

            out.add(new Index(relationIndexName(field), List.of(field), List.of(Sort.Direction.ASC), false, false));
            covered.add(field);
        });

        return out;
    }

    static String relationIndexName(String field) {
        String name = RELATION_PREFIX + field;
        return name.length() <= MAX_NAME ? name : name.substring(0, MAX_NAME);
    }

    /** The indexes MySQL reports, so the plan can be a difference rather than a guess. */
    public static Mono<List<Index>> existing(DSLContext ctx, String db, String table) {

        String sql = "SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, INDEX_TYPE, COLLATION, SEQ_IN_INDEX"
                + " FROM information_schema.STATISTICS"
                + " WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table + "'"
                + " ORDER BY INDEX_NAME, SEQ_IN_INDEX";

        Map<String, List<Object[]>> rows = new LinkedHashMap<>();

        return Flux.from(ctx.resultQuery(sql))
                .doOnNext(r -> rows.computeIfAbsent(String.valueOf(r.get(0)), k -> new ArrayList<>())
                        .add(new Object[] {r.get(1), r.get(2), r.get(3), r.get(4)}))
                .then(Mono.fromSupplier(() -> {
                    List<Index> out = new ArrayList<>();
                    rows.forEach((name, parts) -> {
                        List<String> cols = new ArrayList<>();
                        List<Sort.Direction> directions = new ArrayList<>();
                        boolean unique = false;
                        boolean fullText = false;

                        for (Object[] part : parts) {
                            cols.add(String.valueOf(part[0]));
                            unique = part[1] instanceof Number n && n.intValue() == 0;
                            fullText = "FULLTEXT".equalsIgnoreCase(String.valueOf(part[2]));
                            directions.add("D".equals(String.valueOf(part[3])) ? Sort.Direction.DESC
                                    : Sort.Direction.ASC);
                        }

                        // FULLTEXT reports no collation, so a direction list there
                        // would never match one built from a definition.
                        out.add(new Index(name, cols, fullText ? List.of() : directions, unique, fullText));
                    });
                    return out;
                }));
    }

    /**
     * What turns {@code existing} into {@code desired}.
     *
     * Drops first, because replacing an index means dropping and recreating under
     * the same name and MySQL will not hold two.
     *
     * @param foreignKeyColumns columns a constraint depends on; an index leading with
     *                          one of these is left alone, because MySQL refuses to
     *                          drop it and the attempt fails the whole publish
     */
    public static List<String> sync(
            String db, String table, List<Index> existing, List<Index> desired, Set<String> foreignKeyColumns) {

        Map<String, Index> have = new LinkedHashMap<>();
        for (Index i : existing) have.put(i.name(), i);

        Map<String, Index> want = new LinkedHashMap<>();
        for (Index i : desired) want.put(i.name(), i);

        List<String> drops = new ArrayList<>();
        List<String> adds = new ArrayList<>();

        have.forEach((name, index) -> {
            if ("PRIMARY".equalsIgnoreCase(name)) return;
            if (!index.columns().isEmpty() && foreignKeyColumns.contains(index.columns().getFirst())) return;

            Index target = want.get(name);
            if (target == null || !index.sameAs(target))
                drops.add("DROP INDEX `" + name + "` ON `" + db + "`.`" + table + "`");
        });

        want.forEach((name, index) -> {
            Index current = have.get(name);
            if (current == null || !current.sameAs(index)) adds.add(index.ddl(db, table));
        });

        List<String> all = new ArrayList<>(drops);
        all.addAll(adds);
        return all;
    }
}
