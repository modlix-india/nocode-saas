package com.fincity.saas.commons.core.service.connection.appdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.saas.commons.core.enums.MongoBsonType;
import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumnDefinitions;

/**
 * Checks a storage's column definitions before they are saved, rather than when a
 * table is built from them.
 *
 * Two reasons it has to be at save. The definition fans out to every tenant of the
 * app, so a bad one is found on the first client to be migrated and has already left
 * the rest behind; and the one thing a column definition can do that a schema cannot
 * is contradict the schema, which produces a storage that accepts a write and then
 * rejects the same value on the way back out.
 *
 * That last case is what most of these rules are about. The schema is the API
 * contract on both backends and the physical type has to be one the value can
 * round-trip through: a DECIMAL column hands back a BigDecimal and a DATETIME hands
 * back a date, and the codecs render both as text, so the schema has to say STRING or
 * the next read fails validation on a field nobody touched.
 */
public final class StorageColumnDefinitionValidator {

    private StorageColumnDefinitionValidator() {
    }

    /**
     * Everything wrong with these definitions, in words an author can act on.
     *
     * @param schema        the storage schema, with its own top-level ref already resolved
     * @param relationField fields that exist because of a relation rather than the schema
     */
    public static List<String> problems(
            Map<String, StorageColumnDefinition> definitions, Schema schema, Set<String> relationFields) {

        List<String> out = new ArrayList<>();
        if (definitions == null || definitions.isEmpty()) return out;

        Map<String, Schema> props = schema == null || schema.getProperties() == null
                ? Map.of()
                : schema.getProperties();

        // A derived storage holds only its DIFFERENCE from the base, so one that
        // overrides a column definition and nothing else arrives here with no
        // schema at all. Its fields are real - they are just declared further up
        // the chain - and claiming otherwise refused every such override with
        // "no such field", which is both wrong and the exact opposite of helpful.
        //
        // The field check therefore runs only where the field set is actually
        // known. Everything after it is about the definition itself and runs
        // either way.
        boolean fieldsKnown = !props.isEmpty() || !relationFields.isEmpty();

        definitions.forEach((field, def) -> {
            if (def == null) return;

            if (fieldsKnown && !props.containsKey(field) && !relationFields.contains(field)) {
                // Almost always a typo, and silently ignoring it means the author
                // believes a column is configured when nothing reads the entry.
                out.add(field + ": no such field in this storage");
                return;
            }

            Set<SchemaType> types = typesOf(props.get(field));

            out.addAll(MySQLColumnDefinitions.problems(field, def.getMysql(), fieldsKnown));
            checkMySQLAgainstSchema(field, def, types, out);
            checkMongo(field, def, types, out, fieldsKnown);
        });

        return out;
    }

    private static void checkMySQLAgainstSchema(
            String field, StorageColumnDefinition def, Set<SchemaType> types, List<String> out) {

        if (def.getMysql() == null || def.getMysql().getType() == null || types.isEmpty()) return;

        MySQLColumnType type = def.getMysql().getType();

        if ((type.temporal() || type.exactDecimal()) && !types.contains(SchemaType.STRING))
            out.add(field + ": " + type + " is read back as text, so the schema has to declare this field"
                    + " a STRING. It declares " + types + ", which would pass validation on the way in and"
                    + " fail it on the way out.");
    }

    private static void checkMongo(
            String field,
            StorageColumnDefinition def,
            Set<SchemaType> types,
            List<String> out,
            boolean requireType) {

        if (def.getMongo() == null) return;

        MongoBsonType bson = def.getMongo().getBsonType();
        if (bson == null) {
            // Same reason as the MySQL half: a delta may carry nothing here.
            if (requireType)
                out.add(field + ": mongo.bsonType is required when a mongo column definition is given");
            return;
        }

        if (types.isEmpty()) return;

        switch (bson) {
            case DECIMAL128, DATE -> {
                if (!types.contains(SchemaType.STRING))
                    out.add(field + ": " + bson + " is read back as text, so the schema has to declare this"
                            + " field a STRING. It declares " + types + ".");
            }
            case LONG, INT, DOUBLE -> {
                if (!types.contains(SchemaType.INTEGER)
                        && !types.contains(SchemaType.LONG)
                        && !types.contains(SchemaType.FLOAT)
                        && !types.contains(SchemaType.DOUBLE))
                    out.add(field + ": " + bson + " is read back as a number, so the schema has to declare"
                            + " this field a number. It declares " + types + ".");
            }
            case BOOLEAN -> {
                if (!types.contains(SchemaType.BOOLEAN))
                    out.add(field + ": BOOLEAN needs the schema to declare this field a BOOLEAN, not " + types);
            }
            case STRING -> {
                if (!types.contains(SchemaType.STRING))
                    out.add(field + ": STRING needs the schema to declare this field a STRING, not " + types);
            }
        }
    }

    private static Set<SchemaType> typesOf(Schema field) {
        // A property that is still a ref here is one whose type is not knowable at
        // this point. Refusing it would block a legitimate definition on a referenced
        // type, so the compatibility check is skipped and the arity checks still run.
        if (field == null || field.getType() == null) return Set.of();
        Set<SchemaType> t = field.getType().getAllowedSchemaTypes();
        return t == null ? Set.of() : t;
    }
}
