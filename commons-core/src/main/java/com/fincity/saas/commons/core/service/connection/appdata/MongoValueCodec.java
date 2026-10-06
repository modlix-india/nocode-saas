package com.fincity.saas.commons.core.service.connection.appdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.Decimal128;

import com.fincity.saas.commons.core.enums.MongoBsonType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;

/**
 * Stores a field as the BSON type the author asked for, and hands it back as the
 * schema still describes it.
 *
 * Without this a value is stored as whatever JSON said it was, which for money is a
 * string: unsummable, and compared lexically so that "9.00" sorts above "10.00". The
 * MySQL backend fixes that with a real DECIMAL column, and this is the same fix on the
 * backend that has no columns.
 *
 * Encoding is driven by the declaration, and decoding is driven by the VALUE. That
 * asymmetry is deliberate. Decoding needs no definitions, so it is correct for a row
 * written before the definition existed and for one written after it was removed, and
 * it needs nothing resolved on a path that has no schema in hand. It is also a no-op
 * for every storage as things stand: nothing else in this backend has ever written a
 * Decimal128 or a BSON date into an app data collection, so a document that holds one
 * is a document this codec wrote.
 */
public final class MongoValueCodec {

    private MongoValueCodec() {
    }

    /** Coerce the declared fields of a document about to be written. */
    public static Document encode(Document document, Map<String, StorageColumnDefinition> definitions) {

        if (document == null || definitions == null || definitions.isEmpty()) return document;

        definitions.forEach((field, def) -> {
            if (def == null || def.getMongo() == null || def.getMongo().getBsonType() == null) return;
            if (!document.containsKey(field)) return;

            document.put(field, coerce(document.get(field), def.getMongo().getBsonType()));
        });

        return document;
    }

    /**
     * Turn the two BSON types the schema cannot describe back into text.
     *
     * The API contract is the storage schema, and it calls both of these a STRING -
     * which is why the validator refuses either declaration on a field that is not
     * one. Handing back a Decimal128 would serialise as an object and handing back a
     * Date would serialise as whatever the default locale renders, and in both cases
     * reading a row and writing it back unchanged would fail validation on a field
     * nobody touched.
     */
    public static Document decode(Document document) {

        if (document == null || document.isEmpty()) return document;

        for (Map.Entry<String, Object> e : document.entrySet()) {
            Object decoded = readable(e.getValue());
            if (decoded != e.getValue()) e.setValue(decoded);
        }

        return document;
    }

    private static Object readable(Object value) {

        if (value instanceof Decimal128 d) return d.bigDecimalValue().toPlainString();
        if (value instanceof BigDecimal d) return d.toPlainString();

        if (value instanceof Date d)
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                    d.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime());

        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            boolean changed = false;
            for (Object o : list) {
                Object r = readable(o);
                changed |= r != o;
                out.add(r);
            }
            return changed ? out : value;
        }

        return value;
    }

    private static BsonValue coerce(Object value, MongoBsonType type) {

        if (value == null || value instanceof BsonNull) return BsonNull.VALUE;

        if (value instanceof org.bson.BsonArray arr) {
            org.bson.BsonArray out = new org.bson.BsonArray(arr.size());
            for (BsonValue v : arr) out.add(coerce(v, type));
            return out;
        }

        return switch (type) {
            case DECIMAL128 -> decimal(value);
            case LONG -> new BsonInt64(number(value).longValue());
            case INT -> new BsonInt32(number(value).intValue());
            case DOUBLE -> new BsonDouble(number(value).doubleValue());
            case DATE -> date(value);
            case BOOLEAN -> BsonBoolean.valueOf(bool(value));
            case STRING -> new BsonString(text(value));
        };
    }

    private static BsonValue decimal(Object value) {

        String s = text(value);
        try {
            return new BsonDecimal128(new Decimal128(new BigDecimal(s.trim())));
        } catch (NumberFormatException | ArithmeticException e) {
            // The schema validator should already have refused this. Storing the text
            // it gave us keeps the row writable and keeps the bad value visible,
            // rather than failing a write on a field the caller may not own.
            return new BsonString(s);
        }
    }

    /**
     * Accepts both shapes a date arrives in, because both are real.
     *
     * Dates on this backend have always been epoch numbers, and the schema's date
     * formats describe ISO text. A field being given a real BSON date is very often
     * one being migrated from the first to the second, and refusing one of them would
     * make the declaration unusable on exactly the storages that need it.
     */
    private static BsonValue date(Object value) {

        if (value instanceof BsonDateTime dt) return dt;

        if (isNumeric(value)) {
            long n = number(value).longValue();
            // Ten digits is a second count; thirteen is milliseconds. The boundary is
            // year 2286 in seconds and 1970 in millis, so nothing real is ambiguous.
            return new BsonDateTime(Math.abs(n) < 100_000_000_000L ? n * 1000L : n);
        }

        String s = text(value).trim();
        try {
            return new BsonDateTime(Instant.parse(s).toEpochMilli());
        } catch (DateTimeParseException ignored) {
            // Not an instant; try the local forms the schema's own patterns produce.
        }

        try {
            return new BsonDateTime(java.time.LocalDateTime.parse(s)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli());
        } catch (DateTimeParseException ignored) {
            // Not a date-time either.
        }

        try {
            return new BsonDateTime(java.time.LocalDate.parse(s)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli());
        } catch (DateTimeParseException e) {
            return new BsonString(s);
        }
    }

    private static boolean isNumeric(Object value) {
        return value instanceof Number
                || value instanceof BsonInt32
                || value instanceof BsonInt64
                || value instanceof BsonDouble
                || value instanceof BsonDecimal128;
    }

    private static Number number(Object value) {

        if (value instanceof Number n) return n;
        if (value instanceof BsonInt32 i) return i.getValue();
        if (value instanceof BsonInt64 l) return l.getValue();
        if (value instanceof BsonDouble d) return d.getValue();
        if (value instanceof BsonDecimal128 d) return d.getValue().bigDecimalValue();

        try {
            return new BigDecimal(text(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean bool(Object value) {
        if (value instanceof BsonBoolean b) return b.getValue();
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(text(value));
    }

    private static String text(Object value) {
        if (value instanceof BsonString s) return s.getValue();
        if (value instanceof String s) return s;
        if (value instanceof BsonInt32 i) return String.valueOf(i.getValue());
        if (value instanceof BsonInt64 l) return String.valueOf(l.getValue());
        if (value instanceof BsonDouble d) return String.valueOf(d.getValue());
        if (value instanceof BsonDecimal128 d) return d.getValue().bigDecimalValue().toPlainString();
        return String.valueOf(value);
    }
}
