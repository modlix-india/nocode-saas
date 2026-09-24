package com.fincity.saas.commons.core.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;

/**
 * One gate for every URL a connection hands to the platform to call on the
 * caller's behalf.
 *
 * Every REST connection routes through {@code BasicRestService}, which composes
 * the request URL and calls it from inside our network. Until this existed there
 * was no check on where that URL pointed: a connection whose {@code baseUrl} was
 * {@code http://169.254.169.254/opc/v2/instance/} would have had the instance
 * metadata service fetched and the response handed back to whoever authored the
 * connection. That was tolerable while connection authoring was a staff-only
 * surface in {@code appbuilder}. It stops being tolerable the moment a self-serve
 * tenant can author one.
 *
 * <b>Two entry points, and they are not the same check.</b>
 *
 * {@link #validateConnectionDetails} runs at SAVE time over a whole
 * {@code connectionDetails} map. It refuses the obvious cases -- a literal
 * private address, a loopback name, a non-HTTP scheme -- and deliberately
 * performs NO name resolution, because a DNS answer at save time says nothing
 * about the answer at call time. It raises the floor; it is not the boundary.
 *
 * {@link #validateResolved} is the boundary. It runs in
 * {@code BasicRestService.guardTarget} against the composed URL of every
 * outbound call, and it does resolve the host, which catches both DNS rebinding
 * and the case save time cannot see at all: a connection with no
 * {@code baseUrl} takes its URL verbatim from the KIRun request, so nothing was
 * ever stored for save time to check.
 *
 * Rejecting rather than silently stripping is deliberate, for the same reason
 * {@code FileNameUtil} rejects: a connection saved with its {@code baseUrl}
 * quietly removed is a connection that fails later, somewhere else, with a
 * message about a missing key. A 400 naming the offending field is the thing
 * that can be acted on.
 */
public final class OutboundUrlUtil {

    private OutboundUrlUtil() {
    }

    /** The two schemes a connection may ever name. Everything else is refused. */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * Host names that are never a legitimate outbound target. The metadata
     * entries are the cloud instance-metadata endpoints reachable by name; their
     * link-local addresses are caught by the numeric checks below, but a name
     * that resolves to one is not, so both halves are needed.
     */
    private static final Set<String> BLOCKED_NAMES = Set.of(
            "localhost",
            "ip6-localhost",
            "ip6-loopback",
            "metadata.google.internal",
            "metadata.goog",
            "instance-data",
            "metadata");

    /**
     * Suffixes that mark a name as internal by convention. {@code .oraclevcn.com}
     * is ours specifically: every service host inside the VCN sits under it, so a
     * connection naming one is reaching a sibling service rather than a third
     * party. Move this to configuration if the infrastructure ever changes.
     */
    private static final Set<String> BLOCKED_SUFFIXES = Set.of(
            ".localhost",
            ".local",
            ".internal",
            ".home.arpa",
            ".oraclevcn.com");

    /**
     * A value is treated as a URL only when it carries a scheme followed by an
     * authority. Anything else in {@code connectionDetails} is a header value, a
     * property name or a token, and running URL rules over those would refuse
     * perfectly ordinary strings.
     */
    private static final Pattern WITH_AUTHORITY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*://.*", Pattern.DOTALL);

    /** A scheme with no authority, e.g. {@code file:/etc/passwd}. */
    private static final Pattern SCHEME_ONLY = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]+):(?!//).*", Pattern.DOTALL);

    /**
     * Schemes worth refusing even without an authority, because each is a known
     * way to turn a URL fetch into a local read or a protocol downgrade.
     */
    private static final Set<String> DANGEROUS_SCHEMES = Set.of(
            "file", "ftp", "ftps", "sftp", "tftp", "gopher", "jar", "netdoc", "dict", "ldap", "ldaps", "classpath");

    /**
     * Hosts made only of digits, dots, a hex prefix or colons are address
     * literals, so {@link InetAddress#getByName} parses them without touching
     * DNS. This is what makes the numeric checks safe to run at save time.
     */
    private static final Pattern NUMERIC_HOST = Pattern.compile("^(\\[.*\\]|[0-9.]+|0[xX][0-9a-fA-F.]+|[0-9a-fA-F:.]*:[0-9a-fA-F:.]*)$");

    /** Keys whose value is a bare host rather than a URL, e.g. {@code mail.smtp.host}. */
    private static boolean isHostKey(String key) {
        if (key == null)
            return false;
        String k = key.toLowerCase(Locale.ROOT);
        int dot = k.lastIndexOf('.');
        String last = dot < 0 ? k : k.substring(dot + 1);
        return last.equals("host") || last.equals("hostname");
    }

    /**
     * Walk a whole {@code connectionDetails} map, validating every URL and every
     * host-bearing key at any depth. The map is free-form and drifts -- prod
     * carries keys no code reads -- so a fixed list of keys to check would go
     * stale. Walking everything does not.
     */
    public static void validateConnectionDetails(Map<String, Object> connectionDetails) {
        if (connectionDetails == null || connectionDetails.isEmpty())
            return;

        walk("", connectionDetails);
    }

    private static void walk(String path, Object value) {

        switch (value) {
            case null -> {
            }
            case Map<?, ?> map -> map.forEach((k, v) -> walk(path.isEmpty()
                    ? String.valueOf(k)
                    : path + "." + k, v));
            case Collection<?> list -> {
                int i = 0;
                for (Object item : list)
                    walk(path + "[" + (i++) + "]", item);
            }
            case String s -> validateValue(path, s);
            default -> {
                // Numbers and booleans cannot carry a host.
            }
        }
    }

    private static void validateValue(String key, String value) {

        if (value == null || value.isBlank())
            return;

        String trimmed = value.trim();

        if (WITH_AUTHORITY.matcher(trimmed).matches()) {
            validateUrl(key, trimmed);
            return;
        }

        var schemeOnly = SCHEME_ONLY.matcher(trimmed);
        if (schemeOnly.matches() && DANGEROUS_SCHEMES.contains(schemeOnly.group(1).toLowerCase(Locale.ROOT)))
            throw refuse(key, trimmed, "the '" + schemeOnly.group(1).toLowerCase(Locale.ROOT)
                    + "' scheme is not a web address the platform will fetch");

        if (isHostKey(key))
            checkHost(key, trimmed, trimmed);
    }

    /**
     * Validate one absolute URL. Public so a caller holding a single URL rather
     * than a details map can use the same rules.
     */
    public static void validateUrl(String key, String url) {

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw refuse(key, url, "it is not a valid web address");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme))
            throw refuse(key, url, "only http and https addresses can be called"
                    + (scheme.isEmpty() ? "" : ", not '" + scheme + "'"));

        String host = uri.getHost();

        // A URL whose authority the parser cannot read as a host is refused
        // rather than guessed at: userinfo tricks and bracket abuse both land
        // here, and neither has a legitimate form.
        if (host == null || host.isBlank())
            throw refuse(key, url, "the address has no readable host name");

        checkHost(key, url, host);
    }

    /**
     * The same checks as {@link #validateUrl}, plus the one that save time
     * deliberately skips: what the host actually resolves to, right now.
     *
     * This is the enforcement boundary, and it belongs at CALL time for two
     * reasons. A hostname that resolves publicly when a connection is saved can
     * resolve to 127.0.0.1 an hour later, which is the whole of DNS rebinding.
     * And {@code BasicRestService} only prepends the connection's base URL when
     * it has one -- a connection with no {@code baseUrl} takes the URL verbatim
     * from the KIRun request, so save time never sees the address at all.
     *
     * <b>This resolves DNS and therefore blocks.</b> Every caller is reactive,
     * so it has to be run on a scheduler that tolerates that; see
     * {@code BasicRestService.guardTarget}.
     */
    public static void validateResolved(String url) {

        // Rewrap anything the save-time rules refuse: their wording is about
        // saving, and this path is a call that is already being made. "Cannot be
        // saved" on a live request sends whoever reads it looking at the wrong
        // screen.
        try {
            validateUrl(null, url);
        } catch (GenericException e) {
            throw refuseCall(url, strip(e.getMessage()));
        }

        String host;
        try {
            host = normalizeHost(new URI(url.trim()).getHost());
        } catch (Exception e) {
            throw refuseCall(url, "it is not a valid web address");
        }

        // An address literal was already decided by validateUrl. Resolving it
        // again would only ask the OS to parse the same digits.
        if (NUMERIC_HOST.matcher(host).matches())
            return;

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw refuseCall(url, "'" + host + "' does not resolve to any address");
        }

        // EVERY answer has to be public. A name that returns one public address
        // and one private one is the interesting case, not an edge case.
        for (InetAddress address : addresses)
            if (isPrivateAddress(address))
                throw refuseCall(url, "'" + host + "' resolves to "
                        + address.getHostAddress() + ", which is a private or internal address");
    }

    private static String normalizeHost(String rawHost) {

        if (rawHost == null)
            return "";

        String host = rawHost.trim().toLowerCase(Locale.ROOT);

        // URI.getHost keeps the brackets on an IPv6 literal.
        if (host.startsWith("[") && host.endsWith("]"))
            host = host.substring(1, host.length() - 1);

        // A trailing dot is the DNS root and makes "localhost." bypass a plain
        // equality check while resolving to exactly the same place.
        while (host.endsWith("."))
            host = host.substring(0, host.length() - 1);

        return host;
    }

    private static void checkHost(String key, String original, String rawHost) {

        String host = normalizeHost(rawHost);

        if (host.isEmpty())
            throw refuse(key, original, "the address has no readable host name");

        if (BLOCKED_NAMES.contains(host))
            throw refuse(key, original, "'" + host + "' is this server, not an outside address");

        for (String suffix : BLOCKED_SUFFIXES)
            if (host.endsWith(suffix))
                throw refuse(key, original, "'" + host + "' is an internal address");

        if (!NUMERIC_HOST.matcher(host).matches())
            return;

        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw refuse(key, original, "'" + host + "' is not a usable address");
        }

        if (isPrivateAddress(address))
            throw refuse(key, original, "'" + host + "' is a private or internal address");
    }

    /**
     * Every range that is not a public internet destination. {@code isSiteLocal}
     * covers 10/8, 172.16/12 and 192.168/16; the rest of the RFC 1918 family and
     * its IPv6 equivalents have no method on {@link InetAddress}, so they are
     * checked against the raw bytes.
     */
    private static boolean isPrivateAddress(InetAddress address) {

        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress())
            return true;

        byte[] b = address.getAddress();

        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;

            // 0.0.0.0/8, "this network".
            if (first == 0)
                return true;

            // 100.64.0.0/10, carrier-grade NAT.
            if (first == 100 && second >= 64 && second <= 127)
                return true;

            // 192.0.0.0/24 and 198.18.0.0/15, IETF protocol assignments and
            // benchmarking; neither is routable and both sit inside networks.
            if (first == 192 && second == 0 && (b[2] & 0xFF) == 0)
                return true;

            return first == 198 && (second == 18 || second == 19);
        }

        // fc00::/7, IPv6 unique local.
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    /** The reason out of a save-time refusal, without its saving-specific framing. */
    private static String strip(String message) {
        if (message == null) return "it is not an address this platform will call";
        int colon = message.indexOf("\": ");
        String why = colon < 0 ? message : message.substring(colon + 3);
        int stop = why.indexOf(". A connection may only");
        return stop < 0 ? why : why.substring(0, stop);
    }

    private static GenericException refuseCall(String url, String why) {
        return new GenericException(HttpStatus.BAD_REQUEST,
                "Refusing to call \"" + url + "\": " + why
                        + ". Only addresses reachable from the public internet can be called.");
    }

    private static GenericException refuse(String key, String value, String why) {
        String where = key == null || key.isBlank() ? "A connection address" : "'" + key + "'";
        return new GenericException(HttpStatus.BAD_REQUEST,
                where + " cannot be saved as \"" + value + "\": " + why
                        + ". A connection may only point at an address reachable from the public internet.");
    }

    /** True when the details would be refused, for callers that filter rather than fail. */
    public static boolean isUsable(Map<String, Object> connectionDetails) {
        try {
            validateConnectionDetails(connectionDetails);
            return true;
        } catch (GenericException e) {
            return false;
        }
    }
}
