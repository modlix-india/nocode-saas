package com.fincity.saas.commons.core.model;

import com.fincity.saas.commons.core.enums.MongoBsonType;
import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * What a field should physically be, on each backend, when the schema is not enough.
 *
 * The schema says what a value MEANS and the type mapper guesses a physical shape from
 * it. That guess is right most of the time and cannot be right always: a price is a
 * STRING with format DECIMAL, and only the author knows whether that is money to two
 * places, a tax rate to six, or a quantity that is never fractional at all. Declaring
 * it here replaces the guess without weakening the schema, which still validates every
 * write on both backends.
 *
 * Deliberately per backend rather than one abstract type. The two have genuinely
 * different questions - MySQL wants a column type, Mongo wants a BSON type - and
 * inventing a shared vocabulary would mean neither could say what it needs. The halves
 * are independent: setting one does not imply the other, and a storage that never
 * moves to MySQL can still fix how Mongo stores its money.
 *
 * Separate from {@code fieldDefinitionMap}, which looks similar and is not: that one
 * belongs to the form designer and holds labels, placeholders and editor types.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StorageColumnDefinition implements Serializable, IDifferentiable<StorageColumnDefinition> {

    @Serial
    private static final long serialVersionUID = 7724458216350116473L;

    private MySQL mysql;
    private Mongo mongo;

    public StorageColumnDefinition(StorageColumnDefinition def) {
        this.mysql = def.mysql == null ? null : new MySQL(def.mysql);
        this.mongo = def.mongo == null ? null : new Mongo(def.mongo);
    }

    /**
     * The difference, where {@code this} is the BASE and {@code inc} is the derived
     * document.
     *
     * That order is the framework's and it is not the obvious one:
     * {@code DifferenceExtractor} calls {@code existing.extractDifference(incoming)}.
     * Written the other way round - keeping {@code this} where the two differ - the
     * delta ends up holding the BASE's value, so a client's override saves with a
     * 200 and silently reverts. A unit test written with the operands the same way
     * round passes happily while that happens, which is how it survived until a
     * three-level test looked at what was actually stored.
     */
    @Override
    public Mono<StorageColumnDefinition> extractDifference(StorageColumnDefinition inc) {

        // The derived document says nothing, so it overrides nothing.
        if (inc == null) return Mono.just(new StorageColumnDefinition());

        StorageColumnDefinition diff = new StorageColumnDefinition();

        return Mono.zip(
                        this.mysql == null
                                ? Mono.just(inc.mysql == null ? new MySQL() : new MySQL(inc.mysql))
                                : this.mysql.extractDifference(inc.mysql),
                        this.mongo == null
                                ? Mono.just(inc.mongo == null ? new Mongo() : new Mongo(inc.mongo))
                                : this.mongo.extractDifference(inc.mongo))
                .map(t -> {
                    diff.mysql = inc.mysql == null ? null : t.getT1();
                    diff.mongo = inc.mongo == null ? null : t.getT2();
                    return diff;
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageColumnDefinition.extractDifference"));
    }

    @Override
    public Mono<StorageColumnDefinition> applyOverride(StorageColumnDefinition override) {
        if (override == null) return Mono.just(this);

        if (this.mysql == null) this.mysql = override.mysql == null ? null : new MySQL(override.mysql);
        else if (override.mysql != null) this.mysql.applyOverrideInPlace(override.mysql);

        if (this.mongo == null) this.mongo = override.mongo == null ? null : new Mongo(override.mongo);
        else if (override.mongo != null) this.mongo.applyOverrideInPlace(override.mongo);

        return Mono.just(this).contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageColumnDefinition.applyOverride"));
    }

    /**
     * The physical MySQL column.
     *
     * {@code type} alone is the whole decision; the rest are its arguments. Which
     * arguments are required is not a style choice - see {@link MySQLColumnType} for
     * why a DECIMAL without a precision would make every reconcile ALTER the table
     * again.
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class MySQL implements Serializable, IDifferentiable<MySQL> {

        @Serial
        private static final long serialVersionUID = -2171885447849827746L;

        private MySQLColumnType type;

        /** For CHAR, VARCHAR, BINARY and VARBINARY. */
        private Integer length;

        /** Total digits for DECIMAL; fractional-second digits for DATETIME/TIME/TIMESTAMP. */
        private Integer precision;

        /** Digits after the point, for DECIMAL. */
        private Integer scale;

        private Boolean unsigned;

        /**
         * A collation name, for the textual types.
         *
         * Worth having because it is the only way to make a column compare
         * case-sensitively, which is what anyone storing a code or a slug actually
         * wants and is not something the schema can express.
         */
        private String collation;

        public MySQL(MySQL m) {
            this.type = m.type;
            this.length = m.length;
            this.precision = m.precision;
            this.scale = m.scale;
            this.unsigned = m.unsigned;
            this.collation = m.collation;
        }

        /** {@code this} is the base, {@code inc} the derived: the delta keeps what the derived changed. */
        @Override
        public Mono<MySQL> extractDifference(MySQL inc) {

            if (inc == null) return Mono.just(new MySQL());

            MySQL diff = new MySQL();
            diff.type = CommonsUtil.safeEquals(this.type, inc.type) ? null : inc.type;
            diff.length = CommonsUtil.safeEquals(this.length, inc.length) ? null : inc.length;
            diff.precision = CommonsUtil.safeEquals(this.precision, inc.precision) ? null : inc.precision;
            diff.scale = CommonsUtil.safeEquals(this.scale, inc.scale) ? null : inc.scale;
            diff.unsigned = CommonsUtil.safeEquals(this.unsigned, inc.unsigned) ? null : inc.unsigned;
            diff.collation = CommonsUtil.safeEquals(this.collation, inc.collation) ? null : inc.collation;

            return Mono.just(diff);
        }

        @Override
        public Mono<MySQL> applyOverride(MySQL override) {
            this.applyOverrideInPlace(override);
            return Mono.just(this);
        }

        void applyOverrideInPlace(MySQL override) {
            if (override == null) return;
            if (this.type == null) this.type = override.type;
            if (this.length == null) this.length = override.length;
            if (this.precision == null) this.precision = override.precision;
            if (this.scale == null) this.scale = override.scale;
            if (this.unsigned == null) this.unsigned = override.unsigned;
            if (this.collation == null) this.collation = override.collation;
        }
    }

    /** How the field should be stored in MongoDB, when the JSON type is the wrong one. */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class Mongo implements Serializable, IDifferentiable<Mongo> {

        @Serial
        private static final long serialVersionUID = 4451300823997316744L;

        private MongoBsonType bsonType;

        public Mongo(Mongo m) {
            this.bsonType = m.bsonType;
        }

        @Override
        public Mono<Mongo> extractDifference(Mongo inc) {

            if (inc == null) return Mono.just(new Mongo());

            Mongo diff = new Mongo();
            diff.bsonType = CommonsUtil.safeEquals(this.bsonType, inc.bsonType) ? null : inc.bsonType;
            return Mono.just(diff);
        }

        @Override
        public Mono<Mongo> applyOverride(Mongo override) {
            this.applyOverrideInPlace(override);
            return Mono.just(this);
        }

        void applyOverrideInPlace(Mongo override) {
            if (override == null) return;
            if (this.bsonType == null) this.bsonType = override.bsonType;
        }
    }
}
