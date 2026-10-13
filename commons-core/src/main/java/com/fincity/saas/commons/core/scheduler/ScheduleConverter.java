package com.fincity.saas.commons.core.scheduler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.scheduling.support.CronExpression;

import com.fincity.saas.commons.core.model.ScheduleModel;

/**
 * Between a schedule as a person describes it, in a time zone of their choice, and
 * the cron the scheduler stores, which the builder always writes in UTC.
 *
 * <p>
 * {@link #toUtcCron} turns a builder model read in a zone into a five-field UTC
 * cron; {@link #fromCron} reads a stored cron back into the builder in a zone. Both
 * shift by ONE fixed offset, the zone's offset now: a zone that observes daylight
 * saving therefore runs an hour off its local clock for part of the year, which is
 * the price of storing UTC, and {@link #observesDst} lets the screen say so.
 *
 * <p>
 * Only shapes the builder can draw are recognised. Anything else comes back as
 * {@code custom}, shown and edited as the raw UTC cron, so nothing is rewritten
 * behind the author's back.
 *
 * <p>
 * Stateless and pure: the appbuilder's schedule screen is built from basic
 * components and calls this through EventDefinitionController rather than doing
 * date arithmetic in expressions.
 */
public final class ScheduleConverter {

    public static final List<Integer> MINUTE_STEPS = List.of(1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30);
    public static final List<Integer> HOUR_STEPS = List.of(1, 2, 3, 4, 6, 8, 12);

    private static final List<Integer> ALL_DAYS = List.of(0, 1, 2, 3, 4, 5, 6);
    private static final int DAY_MINUTES = 1440;

    private static final Map<String, String> MACROS = Map.of(
            "@hourly", "0 * * * *",
            "@daily", "0 0 * * *",
            "@midnight", "0 0 * * *",
            "@weekly", "0 0 * * 0",
            "@monthly", "0 0 1 * *",
            "@yearly", "0 0 1 1 *",
            "@annually", "0 0 1 1 *");

    private static final List<String> DAY_NAMES = List.of("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");
    private static final List<String> DAY_LABELS = List.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");

    private static final Pattern STEP = Pattern.compile("^(?:\\*|0)/(\\d+)$");
    private static final Pattern NUMBER = Pattern.compile("^\\d+$");

    private static final DateTimeFormatter RUN_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm",
            Locale.ENGLISH);

    private ScheduleConverter() {
    }

    // ---------------------------------------------------------------- zones

    /** Minutes {@code zone} is ahead of UTC at {@code at}: +330 for Asia/Kolkata. */
    public static int offsetMinutes(ZoneId zone, Instant at) {
        return zone.getRules().getOffset(at).getTotalSeconds() / 60;
    }

    /** True when the zone's offset differs between January and July of {@code year}. */
    public static boolean observesDst(ZoneId zone, int year) {
        return offsetMinutes(zone, java.time.LocalDate.of(year, 1, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
                != offsetMinutes(zone, java.time.LocalDate.of(year, 7, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    }

    public static String formatOffset(int minutes) {
        int a = Math.abs(minutes);
        return String.format("UTC%s%02d:%02d", minutes < 0 ? "-" : "+", a / 60, a % 60);
    }

    // ---------------------------------------------------------------- to cron

    /** The model, read in a zone {@code offset} minutes ahead of UTC, as a UTC cron. Empty for "off". */
    public static String toUtcCron(ScheduleModel m, int offset) {

        switch (frequency(m)) {
            case "off":
                return "";
            case "custom":
                return m.getCron() == null ? "" : m.getCron().trim();
            case "minutes": {
                int every = nz(m.getEvery(), 5);
                return every <= 1 ? "* * * * *" : "*/" + every + " * * * *";
            }
            case "hours": {
                int every = Math.max(1, nz(m.getEvery(), 1));
                int minute = nz(m.getMinute(), 0);
                if (every == 1)
                    return mod(minute - offset, 60) + " * * * *";
                TreeSet<Integer> hours = new TreeSet<>();
                for (int h = mod(nz(m.getHour(), 0), every); h < 24; h += every)
                    hours.add(Math.floorDiv(mod(h * 60 + minute - offset, DAY_MINUTES), 60));
                return mod(minute - offset, 60) + " " + join(hours) + " * * *";
            }
            case "weekly": {
                Shift s = shift(nz(m.getHour(), 9), nz(m.getMinute(), 0), -offset);
                List<Integer> days = m.getDays() == null ? List.of() : m.getDays();
                String dow = days.isEmpty() || days.size() >= 7 ? "*"
                        : join(days.stream().map(d -> mod(d + s.days, 7)).collect(Collectors.toCollection(TreeSet::new)));
                return s.minute + " " + s.hour + " * * " + dow;
            }
            case "monthly": {
                Shift s = shift(nz(m.getHour(), 9), nz(m.getMinute(), 0), -offset);
                return s.minute + " " + s.hour + " " + shiftDayOfMonth(m.getDayOfMonth(), s.days) + " * *";
            }
            default:
                throw new IllegalArgumentException("Unknown frequency: " + m.getFrequency());
        }
    }

    // ---------------------------------------------------------------- from cron

    /**
     * A stored cron, read in a zone {@code delta} minutes behind the display zone
     * (display offset minus the stored zone's), as a builder model in the display
     * zone.
     */
    public static ScheduleModel fromCron(String cron, int delta) {

        String raw = cron == null ? "" : cron.trim();
        if (raw.isEmpty())
            return base().setFrequency("off");

        ScheduleModel custom = base().setFrequency("custom").setCron(raw);
        List<String> f = normalise(raw);
        if (f == null)
            return custom;

        String mi = f.get(0), ho = f.get(1), dom = f.get(2), mon = f.get(3), dow = f.get(4);
        if (!"*".equals(mon))
            return custom;

        if ("*".equals(ho) && "*".equals(dom) && "*".equals(dow)) {
            if ("*".equals(mi))
                return base().setFrequency("minutes").setEvery(1);
            Matcher step = STEP.matcher(mi);
            if (step.matches()) {
                int every = Integer.parseInt(step.group(1));
                return MINUTE_STEPS.contains(every) ? base().setFrequency("minutes").setEvery(every) : custom;
            }
            if (isNum(mi, 59))
                return base().setFrequency("hours").setEvery(1).setHour(0)
                        .setMinute(mod(Integer.parseInt(mi) + delta, 60));
            return custom;
        }

        if (!isNum(mi, 59))
            return custom;
        int m = Integer.parseInt(mi);
        List<Integer> hours = expand(ho, 0, 23, null);
        if (hours == null || hours.isEmpty())
            return custom;

        if (hours.size() > 1) {
            if (!"*".equals(dom) || !"*".equals(dow))
                return custom;
            List<Integer> local = hours.stream().map(h -> mod(h * 60 + m + delta, DAY_MINUTES)).toList();
            int minute = local.getFirst() % 60;
            List<Integer> lh = local.stream().map(t -> t / 60).sorted().toList();
            if (24 % lh.size() != 0)
                return custom;
            int every = 24 / lh.size();
            if (!HOUR_STEPS.contains(every))
                return custom;
            for (int i = 1; i < lh.size(); i++)
                if (lh.get(i) - lh.get(i - 1) != every)
                    return custom;
            return base().setFrequency("hours").setEvery(every).setHour(lh.getFirst()).setMinute(minute);
        }

        Shift s = shift(hours.getFirst(), m, delta);

        if ("*".equals(dom)) {
            List<Integer> days = "*".equals(dow) ? ALL_DAYS : expand(dow, 0, 7, DAY_NAMES);
            if (days == null || days.isEmpty())
                return custom;
            List<Integer> local = days.stream().map(d -> mod(d % 7 + s.days, 7)).distinct().sorted().toList();
            return base().setFrequency("weekly").setHour(s.hour).setMinute(s.minute).setDays(local);
        }

        if (!"*".equals(dow))
            return custom;
        String day = unshiftDayOfMonth(dom, s.days);
        return day == null ? custom
                : base().setFrequency("monthly").setHour(s.hour).setMinute(s.minute).setDayOfMonth(day);
    }

    // ---------------------------------------------------------------- reading

    /** Throws IllegalArgumentException with Spring's own message when the scheduler could not read it. */
    public static CronExpression parse(String cron) {
        return CronGrid.parse(cron);
    }

    /** The next {@code count} runs of a cron read in {@code cronZone}, after {@code from}, shown in {@code display}. */
    public static List<String> nextRuns(String cron, ZoneId cronZone, ZoneId display, Instant from, int count) {

        CronExpression expr = parse(cron);
        List<String> out = new ArrayList<>();
        ZonedDateTime t = from.atZone(cronZone);
        for (int i = 0; i < count; i++) {
            t = expr.next(t);
            if (t == null)
                break;
            out.add(t.withZoneSameInstant(display).format(RUN_FORMAT));
        }
        return out;
    }

    /** One line a person can read, for a model in {@code zoneLabel}. */
    public static String describe(ScheduleModel m, String zoneLabel) {

        String at = time(nz(m.getHour(), 0), nz(m.getMinute(), 0));
        String in = zoneLabel == null ? "" : " (" + zoneLabel + ")";

        return switch (frequency(m)) {
            case "off" -> "Not scheduled";
            case "custom" -> "Custom cron " + (m.getCron() == null ? "" : m.getCron()) + " (UTC)";
            case "minutes" -> nz(m.getEvery(), 1) <= 1 ? "Every minute" : "Every " + m.getEvery() + " minutes";
            case "hours" -> nz(m.getEvery(), 1) <= 1
                    ? "Every hour at :" + String.format("%02d", nz(m.getMinute(), 0)) + in
                    : "Every " + m.getEvery() + " hours from " + at + in;
            case "weekly" -> {
                List<Integer> d = m.getDays() == null ? List.of() : m.getDays();
                String days = d.size() >= 7 || d.isEmpty() ? "Every day"
                        : d.equals(List.of(1, 2, 3, 4, 5)) ? "Weekdays"
                                : d.stream().map(DAY_LABELS::get).collect(Collectors.joining(", "));
                yield days + " at " + at + in;
            }
            case "monthly" -> ("L".equals(m.getDayOfMonth()) ? "Last day" : "Day " + m.getDayOfMonth())
                    + " of every month at " + at + in;
            default -> m.getFrequency();
        };
    }

    // ---------------------------------------------------------------- helpers

    private static ScheduleModel base() {
        return new ScheduleModel().setFrequency("off").setEvery(5).setMinute(0).setHour(9)
                .setDays(List.of(1, 2, 3, 4, 5)).setDayOfMonth("1").setCron("");
    }

    private static String frequency(ScheduleModel m) {
        return m.getFrequency() == null ? "off" : m.getFrequency();
    }

    /** The five fields as Spring reads them, or null when the builder cannot draw it. */
    static List<String> normalise(String cron) {

        String text = cron.trim();
        if (text.startsWith("@")) {
            text = MACROS.get(text.toLowerCase(Locale.ROOT));
            if (text == null)
                return null;
        }

        List<String> fields = new ArrayList<>(Arrays.asList(text.split("\\s+")));
        if (fields.size() == 6) {
            if (!"0".equals(fields.getFirst()))
                return null;
            fields.removeFirst();
        }
        if (fields.size() != 5)
            return null;

        return fields.stream().map(f -> "?".equals(f) ? "*" : f.toUpperCase(Locale.ROOT)).toList();
    }

    private static boolean isNum(String s, int max) {
        return NUMBER.matcher(s).matches() && Integer.parseInt(s) <= max;
    }

    /** The values a cron field matches: lists, ranges, steps, optional names. Null when it is not one of those. */
    static List<Integer> expand(String field, int min, int max, List<String> names) {

        TreeSet<Integer> out = new TreeSet<>();
        for (String part : field.split(",")) {
            String[] rs = part.split("/", -1);
            String range = rs[0];
            int step;
            try {
                step = rs.length > 1 ? Integer.parseInt(rs[1]) : 1;
            } catch (NumberFormatException e) {
                return null;
            }
            if (step < 1)
                return null;

            Integer lo, hi;
            if ("*".equals(range)) {
                lo = min;
                hi = max;
            } else if (range.contains("-")) {
                String[] ab = range.split("-", -1);
                lo = value(ab[0], names);
                hi = ab.length > 1 ? value(ab[1], names) : null;
            } else {
                lo = value(range, names);
                hi = rs.length > 1 ? Integer.valueOf(max) : lo;
            }
            if (lo == null || hi == null || lo < min || hi > max || lo > hi)
                return null;
            for (int v = lo; v <= hi; v += step)
                out.add(v);
        }
        return new ArrayList<>(out);
    }

    private static Integer value(String s, List<String> names) {
        if (names != null) {
            int i = names.indexOf(s);
            if (i >= 0)
                return i;
        }
        return NUMBER.matcher(s).matches() ? Integer.valueOf(s) : null;
    }

    private record Shift(int hour, int minute, int days) {
    }

    private static Shift shift(int hour, int minute, int by) {
        int t = hour * 60 + minute + by;
        int local = mod(t, DAY_MINUTES);
        return new Shift(local / 60, local % 60, Math.floorDiv(t, DAY_MINUTES));
    }

    private static String shiftDayOfMonth(String day, int shift) {
        if (day == null || "L".equals(day))
            return shift == 0 ? "L" : shift > 0 ? "1" : "L-1";
        int d = Integer.parseInt(day) + shift;
        return d < 1 ? "L" : String.valueOf(d);
    }

    private static String unshiftDayOfMonth(String dom, int shift) {
        if ("L".equals(dom))
            return shift == 0 ? "L" : shift > 0 ? "1" : null;
        if ("L-1".equals(dom))
            return shift == 1 ? "L" : null;
        if (!isNum(dom, 31))
            return null;
        int d = Integer.parseInt(dom) + shift;
        return d >= 1 && d <= 28 ? String.valueOf(d) : null;
    }

    private static String join(java.util.Collection<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String time(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    private static int mod(int n, int m) {
        return Math.floorMod(n, m);
    }

    private static int nz(Integer v, int fallback) {
        return v == null ? fallback : v;
    }
}
