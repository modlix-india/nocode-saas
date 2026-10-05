package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageRelationConstraint;
import com.fincity.saas.commons.core.enums.StorageTriggerType;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.StorageRelation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Real foreign keys for the relations MySQL can enforce, so the constraint is the
 * database's job rather than a loop in the service.
 *
 * The difference is not tidiness. The app-code version reads the children, decides,
 * and then deletes, with no lock held across the three - so a row inserted between
 * the read and the delete is orphaned, and nothing ever notices. A foreign key is
 * checked inside the same statement as the delete, against rows no other transaction
 * can slip past. It also holds for writes that never go through the service at all.
 *
 * Three relations cannot become a foreign key, and each falls back to app code:
 * <ul>
 * <li>{@code TO_MANY}, which is a JSON array of ids. MySQL cannot point a foreign key
 * at an element of a JSON document, and no amount of generated columns fixes it for a
 * list whose length is not fixed.</li>
 * <li>a relation whose {@code fieldName} is not the row id, because the referenced
 * column has to be a key and only {@code _id} is one.</li>
 * <li>{@code NOTHING}, which is not a constraint. No key is created, which is also
 * what keeps every relation that exists today exactly as it is: all of them are
 * NOTHING, and adding a key to one would start refusing writes that have always been
 * allowed.</li>
 * </ul>
 *
 * @see #sync for why an orphan check has to come first
 */
public final class MySQLForeignKeys {

    /** MySQL's identifier ceiling. A name built from two of them has to be trimmed. */
    private static final int MAX_NAME = 64;

    private MySQLForeignKeys() {
    }

    /**
     * One foreign key as both sides understand it.
     *
     * @param name        the constraint name, deterministic so a re-run recognises its own work
     * @param column      the referencing column on this table
     * @param targetTable the table whose {@code _id} it points at, in the same tenant schema
     * @param onDelete    RESTRICT or CASCADE
     * @param onUpdate    always RESTRICT in practice; see {@link #action}
     */
    public record ForeignKey(String name, String column, String targetTable, String onDelete, String onUpdate) {

        public String addClause() {
            return "ADD CONSTRAINT `" + this.name + "` FOREIGN KEY (`" + this.column + "`) REFERENCES `"
                    + this.targetTable + "` (`" + MySQLTypeMapper.ID_COLUMN + "`) ON DELETE " + this.onDelete
                    + " ON UPDATE " + this.onUpdate;
        }

        /** Same key, ignoring the name, so a rename is not mistaken for a change. */
        public boolean sameAs(ForeignKey other) {
            return other != null
                    && this.column.equals(other.column)
                    && this.targetTable.equalsIgnoreCase(other.targetTable)
                    && this.onDelete.equalsIgnoreCase(other.onDelete)
                    && this.onUpdate.equalsIgnoreCase(other.onUpdate);
        }
    }

    /**
     * Whether this relation is one the database can enforce.
     *
     * The single place that decides, because the service has to ask the same question
     * to know whether to run its own loop. Two different answers would mean either a
     * constraint enforced twice or one enforced nowhere, and the second is silent.
     */
    public static boolean enforceable(StorageRelation relation) {

        if (relation == null) return false;
        if (relation.getRelationType() != StorageRelationType.TO_ONE) return false;

        String field = relation.getFieldName();
        if (field != null && !field.isBlank() && !MySQLTypeMapper.ID_COLUMN.equals(field)) return false;

        return action(relation.getDeleteConstraint()) != null;
    }

    /**
     * The same question, with the one thing a cascade can take away from us.
     *
     * RESTRICT is a pure guard: it removes no row, so there is nothing the
     * database could skip, and it holds even against a write that never went
     * through the service. CASCADE is different in kind, because it DELETES rows,
     * and a row deleted inside MySQL gets no BEFORE_DELETE or AFTER_DELETE
     * trigger, raises no event and leaves its version rows behind - none of which
     * the database can be told to do. 39 storages are versioned, so this is not a
     * hypothetical.
     *
     * So a cascade goes to the database only when deleting the child owes nothing,
     * and otherwise the service keeps it and the rows go one at a time through the
     * ordinary delete. No key is created in that case: an ON DELETE CASCADE would
     * fire first and an ON DELETE RESTRICT would block the very cascade the author
     * asked for.
     *
     * One gap remains and is not closed here: if the CHILD is itself the target of
     * a constraint the service enforces - a TO_MANY pointing at it, say - a
     * database cascade removes it without that constraint being consulted. Knowing
     * that needs the reverse index, which is a query, and this has to answer
     * without one.
     */
    public static boolean enforceable(Storage child, StorageRelation relation) {

        if (!enforceable(relation)) return false;
        if (relation.getDeleteConstraint() != StorageRelationConstraint.CASCADE) return true;

        return !owesAnythingOnDelete(child);
    }

    static boolean owesAnythingOnDelete(Storage child) {

        if (child == null) return true;

        if (BooleanUtil.safeValueOf(child.getGenerateEvents())) return true;

        // isAudited, not just isVersioned. The backends decide whether to write a
        // version row with keepsHistory() = isAudited || isVersioned, and this asked
        // only about the second half. isAudited DEFAULTS TO TRUE, so almost every
        // cascade was being handed to the database - and ON DELETE CASCADE writes no
        // version row, because it has no caller to write one.
        //
        // Measured on MySQL 8.4 before this line changed: deleting the head of a
        // 4-author / 3-book chain removed all 7 rows and wrote exactly ONE delete
        // version row - the head's, which went through the service. Six rows left an
        // audited storage with no trace at all.
        //
        // RESTRICT is unaffected and still goes to the database: it removes nothing,
        // so there is no history for it to skip.
        if (BooleanUtil.safeValueOf(child.getIsAudited())) return true;
        if (BooleanUtil.safeValueOf(child.getIsVersioned())) return true;

        Map<StorageTriggerType, List<String>> triggers = child.getTriggers();
        if (triggers == null) return false;

        return notEmpty(triggers.get(StorageTriggerType.BEFORE_DELETE))
                || notEmpty(triggers.get(StorageTriggerType.AFTER_DELETE));
    }

    private static boolean notEmpty(List<String> functions) {
        return functions != null && !functions.isEmpty();
    }

    /**
     * The SQL action for a constraint, or null when there is nothing to enforce.
     *
     * {@code NOTHING} is not {@code NO ACTION}. In MySQL, NO ACTION is a synonym for
     * RESTRICT, so mapping it that way would turn every relation in the fleet into a
     * blocking constraint on first publish. NOTHING means no key at all.
     */
    public static String action(StorageRelationConstraint constraint) {
        if (constraint == null) return null;
        return switch (constraint) {
            case CASCADE -> "CASCADE";
            case RESTRICT -> "RESTRICT";
            case NOTHING -> null;
        };
    }

    /**
     * What ON UPDATE should say.
     *
     * Always RESTRICT, and it can never fire. ON UPDATE acts when the REFERENCED key
     * changes, and the referenced key is {@code _id}: a ULID assigned at insert that
     * no code path rewrites. Declaring CASCADE here would be harmless and would also
     * be a lie about what the storage does, so the honest clause is the restrictive
     * one, and {@code updateConstraint} is documented as inert on this backend rather
     * than quietly mapped to something that looks like it works.
     */
    public static String onUpdate() {
        return "RESTRICT";
    }

    /** The keys this table should have, given the relations and the resolved table names. */
    public static List<ForeignKey> desired(
            String table, Map<String, StorageRelation> relations, Map<String, String> targetTableByField) {

        List<ForeignKey> out = new ArrayList<>();
        if (relations == null || relations.isEmpty()) return out;

        relations.forEach((field, relation) -> {
            if (!enforceable(relation)) return;
            // The Storage-aware check belongs to the caller, which holds the
            // child definition; this method is given relations already filtered.

            String target = targetTableByField.get(field);
            if (target == null) return;

            out.add(new ForeignKey(
                    name(table, field), field, target, action(relation.getDeleteConstraint()), onUpdate()));
        });

        return out;
    }

    /**
     * A deterministic name, so a second run sees its own constraint rather than adding
     * another one beside it.
     *
     * Trimmed from the TABLE end when it will not fit, because the column is the part
     * that distinguishes two keys on the same table and the table name is already
     * implied by where the constraint lives.
     */
    static String name(String table, String column) {

        String suffix = "_" + column;
        int room = MAX_NAME - "fk_".length() - suffix.length();

        String head = room <= 0 ? "" : (table.length() <= room ? table : table.substring(0, room));
        return "fk_" + head + suffix;
    }

    /** The foreign keys MySQL reports on this table, so the plan can be a difference. */
    public static Mono<List<ForeignKey>> existing(DSLContext ctx, String db, String table) {

        String sql = "SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, r.DELETE_RULE, r.UPDATE_RULE"
                + " FROM information_schema.KEY_COLUMN_USAGE k"
                + " JOIN information_schema.REFERENTIAL_CONSTRAINTS r"
                + " ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME"
                + " WHERE k.TABLE_SCHEMA = '" + db + "' AND k.TABLE_NAME = '" + table + "'"
                + " AND k.REFERENCED_TABLE_NAME IS NOT NULL"
                + " ORDER BY k.CONSTRAINT_NAME";

        return Flux.from(ctx.resultQuery(sql))
                .map(r -> new ForeignKey(
                        String.valueOf(r.get(0)),
                        String.valueOf(r.get(1)),
                        String.valueOf(r.get(2)),
                        String.valueOf(r.get(3)),
                        String.valueOf(r.get(4))))
                .collectList();
    }

    /**
     * The statements that turn {@code existing} into {@code desired}.
     *
     * Drops come first. A relation being repointed is a drop and an add on the same
     * column, and MySQL will not have two foreign keys on one column pointing at
     * different tables.
     */
    public static List<String> sync(
            String db, String table, List<ForeignKey> existing, List<ForeignKey> desired) {

        Map<String, ForeignKey> have = new LinkedHashMap<>();
        for (ForeignKey fk : existing) have.put(fk.name(), fk);

        Map<String, ForeignKey> want = new LinkedHashMap<>();
        for (ForeignKey fk : desired) want.put(fk.name(), fk);

        List<String> drops = new ArrayList<>();
        List<String> adds = new ArrayList<>();

        have.forEach((name, fk) -> {
            ForeignKey target = want.get(name);
            if (target == null || !fk.sameAs(target))
                drops.add("ALTER TABLE `" + db + "`.`" + table + "` DROP FOREIGN KEY `" + name + "`");
        });

        want.forEach((name, fk) -> {
            ForeignKey current = have.get(name);
            if (current == null || !current.sameAs(fk))
                adds.add("ALTER TABLE `" + db + "`.`" + table + "` " + fk.addClause());
        });

        List<String> all = new ArrayList<>(drops);
        all.addAll(adds);
        return all;
    }

    /**
     * Rows that would make this key impossible to add.
     *
     * Needed because MySQL refuses the whole ALTER if a single row points at an id
     * that is not there, and the error names the constraint rather than the rows. On
     * an existing table that is the difference between a publish that explains itself
     * and one that fails with errno 1452 and leaves the author guessing which of
     * forty thousand rows is wrong.
     */
    public static String orphanCount(String db, String table, ForeignKey fk) {
        return "SELECT COUNT(*) FROM `" + db + "`.`" + table + "` c"
                + " WHERE c.`" + fk.column() + "` IS NOT NULL"
                + " AND NOT EXISTS (SELECT 1 FROM `" + db + "`.`" + fk.targetTable() + "` p"
                + " WHERE p.`" + MySQLTypeMapper.ID_COLUMN + "` = c.`" + fk.column() + "`)";
    }
}
