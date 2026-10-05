package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jooq.JSON;
import org.jooq.JSONB;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts between what callers hand the app data API and what a MySQL JSON column
 * can hold.
 *
 * Needed because a storage's nested fields are ordinary Maps and Lists by the time
 * they reach a backend, and the Mongo backend writes them as they are. A JSON column
 * takes text, and hands text back, so without this a nested field either fails to bind
 * on write or silently returns a string where the caller expected an object - and the
 * second is much the worse of the two, because nothing reports it.
 *
 * Jackson rather than Gson, because round-tripping must not change 1 into 1.0, and
 * Gson's untyped parse makes every number a Double.
 */
public final class MySQLValueCodec {

    /** A trailing Z or +hh:mm, the only offsets the schema's date patterns admit. */
    private static final Pattern OFFSET_SUFFIX = Pattern.compile("(Z|[+-]\\d{2}:\\d{2})$");

    private final ObjectMapper mapper;

    public MySQLValueCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * The row as the driver should see it.
     *
     * Only the named columns are touched; everything else is already a scalar the
     * driver can bind.
     */
    public Map<String, Object> encode(Map<String, Object> row, Set<String> jsonColumns) {
        return this.encode(row, jsonColumns, Set.of());
    }

    /**
     * The row as the driver should see it, with declared dates pinned to UTC.
     *
     * {@code dateColumns} are the same ones {@link #decode} renders back to ISO text.
     * A value that carries an offset or a Z is shifted to UTC and the offset dropped,
     * because a DATETIME column has nowhere to keep it: handed to MySQL as it is, the
     * offset is applied against the session time_zone, which follows the host OS, so
     * the same write lands at different wall times on a UTC server and an IST laptop.
     */
    public Map<String, Object> encode(Map<String, Object> row, Set<String> jsonColumns, Set<String> dateColumns) {
        return this.encode(row, jsonColumns, dateColumns, Set.of());
    }

    /**
     * The row as the driver should see it, with declared dates pinned to UTC and
     * declared decimals bound as exact numbers.
     */
    public Map<String, Object> encode(
            Map<String, Object> row, Set<String> jsonColumns, Set<String> dateColumns, Set<String> decimalColumns) {

        if (row == null
                || row.isEmpty()
                || (jsonColumns.isEmpty() && dateColumns.isEmpty() && decimalColumns.isEmpty())) return row;

        Map<String, Object> out = new LinkedHashMap<>(row);
        row.forEach((k, v) -> {
            if (jsonColumns.contains(k)) out.put(k, this.toJson(v));
            else if (dateColumns.contains(k)) out.put(k, utcText(v));
            else if (decimalColumns.contains(k)) out.put(k, exact(v));
        });
        return out;
    }

    /**
     * An exact decimal, rather than whatever the value happens to be.
     *
     * A price arrives as the string the schema declared, and MySQL would convert it
     * on the server. Converting here instead means a value that cannot be a decimal
     * is caught as a bad request rather than as a driver error two layers down, and
     * it keeps the binding off any locale-dependent parse. A Double is converted
     * through its own toString deliberately: {@code new BigDecimal(0.1)} is
     * 0.1000000000000000055511151231257827, which is exactly the kind of surprise a
     * DECIMAL column is chosen to avoid.
     */
    static Object exact(Object value) {

        if (value == null || value instanceof java.math.BigDecimal) return value;

        if (value instanceof Number n) return new java.math.BigDecimal(n.toString());

        if (value instanceof String s) {
            if (s.isBlank()) return null;
            try {
                return new java.math.BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                // The schema validator should already have refused this. Passing it
                // through lets MySQL give the clearer error about which column.
                return value;
            }
        }

        return value;
    }

    /**
     * ISO text with any offset folded into the value, as UTC.
     *
     * Only a value that carries an offset is rewritten. One without is already taken
     * to be UTC, which is what every app-set timestamp in the platform is, so it goes
     * through exactly as before. Anything that does not parse is also left alone:
     * the validator has already passed it, and MySQL's own error is the better report.
     */
    static Object utcText(Object value) {

        if (!(value instanceof String s) || !OFFSET_SUFFIX.matcher(s).find()) return value;

        try {
            if (s.indexOf('T') != -1)
                return java.time.OffsetDateTime.parse(s)
                        .withOffsetSameInstant(java.time.ZoneOffset.UTC)
                        .toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            return java.time.OffsetTime.parse(s)
                    .withOffsetSameInstant(java.time.ZoneOffset.UTC)
                    .toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (java.time.format.DateTimeParseException e) {
            return value;
        }
    }

    /** The row as the caller expects it, with JSON columns parsed back into structures. */
    public Map<String, Object> decode(Map<String, Object> row, Set<String> jsonColumns) {
        return this.decode(row, jsonColumns, Set.of());
    }

    /**
     * The row as the caller expects it.
     *
     * {@code dateColumns} are the ones the storage schema declares as a STRING with a
     * date format. They are real DATETIME columns here, which is the entire point,
     * but the API contract still says string: the caller sent an ISO-8601 string and
     * the schema validator will only accept one back. Returning the driver's
     * LocalDateTime breaks the most ordinary thing anyone does - read a row, change
     * one field, write it back - because the value that was valid going in is
     * rejected coming out, formatted into whatever the default locale renders.
     */
    public Map<String, Object> decode(Map<String, Object> row, Set<String> jsonColumns, Set<String> dateColumns) {
        return this.decode(row, jsonColumns, dateColumns, Set.of());
    }

    /**
     * The row as the caller expects it.
     *
     * {@code decimalColumns} are here for exactly the reason {@code dateColumns} are.
     * The schema calls the field a STRING with format DECIMAL and the validator will
     * only accept a string back, but the column is a real DECIMAL - which is the
     * whole point, because text cannot be summed - so the driver hands back a
     * BigDecimal. Left alone it serialises as a bare number, and the most ordinary
     * thing anyone does, read a row and write it back with one field changed, fails
     * on a value the caller never touched.
     */
    public Map<String, Object> decode(
            Map<String, Object> row, Set<String> jsonColumns, Set<String> dateColumns, Set<String> decimalColumns) {

        if (row == null
                || row.isEmpty()
                || (jsonColumns.isEmpty() && dateColumns.isEmpty() && decimalColumns.isEmpty())) return row;

        Map<String, Object> out = new LinkedHashMap<>(row);
        row.forEach((k, v) -> {
            if (jsonColumns.contains(k)) out.put(k, this.fromJson(v));
            else if (dateColumns.contains(k)) out.put(k, isoText(v));
            else if (decimalColumns.contains(k)) out.put(k, decimalText(v));
        });
        return out;
    }

    /**
     * Plain decimal text, with the column's scale kept.
     *
     * {@code toPlainString} rather than {@code toString}, because the latter switches
     * to scientific notation past a certain exponent and {@code 1E+3} does not match
     * the schema's decimal pattern. The trailing zeros MySQL returns are kept on
     * purpose: 10.5000 in a DECIMAL(19,4) is what the column holds, and stripping
     * them would make a read-modify-write cycle look like an edit every time.
     */
    static Object decimalText(Object value) {

        if (value instanceof java.math.BigDecimal d) return d.toPlainString();
        if (value instanceof java.math.BigInteger i) return i.toString();
        return value;
    }

    /**
     * ISO-8601, which is the only shape the schema validator accepts.
     *
     * The java.sql types are here because they are what actually arrives. The driver
     * hands back a LocalDateTime, JOOQ converts it on the way through, and a
     * java.sql.Timestamp left alone is serialised by Gson as "Jan 15, 2026, 10:00:00
     * AM" - a locale-formatted string that the validator rejects and that would
     * differ between machines even if it did not.
     */
    static Object isoText(Object value) {

        if (value instanceof java.time.LocalDateTime dt)
            return dt.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (value instanceof java.time.LocalDate d)
            return d.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        if (value instanceof java.time.LocalTime t)
            return t.format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME);
        if (value instanceof java.time.OffsetDateTime o)
            return o.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        if (value instanceof java.sql.Timestamp ts)
            return ts.toLocalDateTime().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (value instanceof java.sql.Date d)
            return d.toLocalDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        if (value instanceof java.sql.Time t)
            return t.toLocalTime().format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME);
        if (value instanceof java.util.Date d)
            return java.time.LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return value;
    }

    public Object toJson(Object value) {

        if (value == null) return null;

        // A value that came back from a read and is being written again.
        if (value instanceof JSON j) return j.data();
        if (value instanceof JSONB j) return j.data();

        // Already text. The ambiguity is real and unavoidable: a string reaching a JSON
        // column is either JSON someone encoded themselves or a plain value that
        // happens to live in a JSON column. Encoding it again would corrupt the first
        // case; leaving it alone would produce invalid JSON in the second, which MySQL
        // rejects outright. Parsing decides, and a parse failure means it was never
        // JSON to begin with.
        if (value instanceof String s) return isJsonText(s) ? s : this.write(s);

        if (value instanceof Map || value instanceof Collection || value.getClass().isArray())
            return this.write(value);

        // A number or boolean in a JSON column is valid JSON as it stands, but the
        // driver would bind it as its own type and MySQL would refuse it.
        return this.write(value);
    }

    Object fromJson(Object value) {

        String s = text(value);
        if (s == null) return value == null ? null : value;
        if (s.isBlank()) return null;

        try {
            return this.mapper.readValue(s, Object.class);
        } catch (JsonProcessingException e) {
            // Something put non-JSON in a JSON column, which MySQL should not have
            // allowed. Handing back the raw text loses nothing and is far better than
            // failing the whole read for one row.
            return s;
        }
    }

    /**
     * The JSON text behind whatever the driver handed back.
     *
     * A JSON column does NOT arrive as a String. JOOQ wraps it in its own
     * {@link JSON} type, which is a perfectly reasonable thing for JOOQ to do and
     * completely wrong to pass on: callers of the app data API get Maps and Lists from
     * the Mongo backend and have no reason to have heard of a JOOQ type. Decoding only
     * Strings would leave it untouched and hand that type straight through - the
     * failure would be a cast somewhere far away from here, in code that is not wrong.
     */
    private static String text(Object value) {

        if (value instanceof String s) return s;
        if (value instanceof JSON j) return j.data();
        if (value instanceof JSONB j) return j.data();
        if (value instanceof byte[] b) return new String(b, java.nio.charset.StandardCharsets.UTF_8);

        return null;
    }

    private String write(Object value) {
        try {
            return this.mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("value cannot be stored as JSON: " + e.getOriginalMessage(), e);
        }
    }

    private boolean isJsonText(String s) {

        String t = s.strip();
        if (t.isEmpty()) return false;

        char c = t.charAt(0);
        // Only structures count. A bare `123` or `"x"` is technically valid JSON, but
        // treating it as already-encoded would mean a user's literal string "null"
        // silently became a null.
        if (c != '{' && c != '[') return false;

        try {
            this.mapper.readTree(t);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
