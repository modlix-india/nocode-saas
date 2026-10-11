package com.fincity.saas.commons.core.functions.security;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.feign.IFeignSecurityAsCaller;
import com.fincity.saas.commons.core.kirun.repository.entityprocessor.EPRemoteFunction;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import feign.FeignException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * User and org administration from a server function, AS THE CALLER: the security REST calls the
 * CRM and partner-management pages make (users/query, users CRUD and status, profile assignment,
 * client status / update / delete, client managers, client activities, an app's profiles), moved
 * behind {@code CoreServices.Security.*}.
 *
 * <p><b>Acting as the caller.</b> Every function calls security's PUBLIC route, the one the page
 * called, through {@link IFeignSecurityAsCaller}, forwarding the caller's own token and the
 * request's {@code X-Forwarded-Host} / {@code X-Forwarded-Port} / {@code clientCode} /
 * {@code appCode}. Security therefore authenticates the same user and applies its own
 * {@code @PreAuthorize} and client checks unchanged; a function can never do more than the user
 * could do from the page. When there is no caller request to forward (an anonymous caller, an
 * event action, a schedule, a storage trigger reached without a function request), the function
 * refuses with 403 and calls nothing: there is no fallback to an internal route.
 *
 * <p><b>Tenant rule, enforced here as well.</b> The client acted on must be the caller's own client
 * or a client the caller's client manages (security's {@code doesClientManageClient}). Never a
 * managing (ancestor) client and never an unrelated one: this is narrower than
 * {@link SecurityFunctionSupport#isRelatedClient}, whose upward partner allowance is for reading
 * a managing client's data, not for administering it. Deleting a client and changing its status
 * accept a MANAGED client only, never the caller's own. Users are placed by their client, read
 * through security's internal user read only for this check (the action itself always goes
 * through the public route as the caller). One upward exception, for READING a user by id only
 * ({@link GetUser}): the user may also belong to the caller's client's direct managing client
 * ({@link SecurityFunctionSupport#isRelatedClient}), so a partner sees the managing client's
 * user its deals are assigned to. Listing users ({@link QueryUsers}) stays own or managed.
 *
 * <p><b>Reads ask security first.</b> {@link QueryUsers}, {@link GetUser} and
 * {@link QueryClientActivities} call security as the caller and apply the tenant rule to what
 * came back, so a refusal security would make anyway (no User READ, unknown user, ...) arrives
 * with security's own status and message.
 *
 * <p><b>Writes are checked first.</b> When the tenant rule refuses a write (or names a user that
 * does not exist), nothing is sent, and the refusal is worded the way security words the same
 * refusal on that route: 403 {@code Access Denied} when the caller lacks the route's
 * {@code @PreAuthorize} authority, else the route's own text (e.g. "Cannot update user for the
 * selected client"). The tenant rule decides; only the wording follows security.
 *
 * <p>Like the other people/org functions these answer only inside a server-side function
 * definition ({@link SecurityFunctionSupport#definitionContextAuthentication}).
 *
 * <p>Errors security answers keep their status and message (a 403 from security stays a 403; a
 * plain-text body such as Spring Security's {@code Access Denied} becomes the message as is).
 */
public final class CallerSecurityFunctions {

    static final String USER_ID = "userId";
    static final String CLIENT_ID = "clientId";
    static final String PROFILE_ID = "profileId";
    static final String STATUS = "status";
    static final String PARTIAL = "partial";
    static final String QUERY = "query";
    static final String USER = "user";
    static final String CLIENT = "client";
    static final String ACTIVITY = "activity";
    static final String MANAGER_IDS = "managerIds";
    static final String NEW_MANAGER_ID = "newManagerId";
    static final String OLD_MANAGER_ID = "oldManagerId";
    static final String FROM_USER_ID = "fromUserId";
    static final String TO_USER_ID = "toUserId";
    static final String REMOVE_MANAGER = "removeManager";
    static final String APP_ID = "appId";
    static final String APP_CODE = "appCode";
    static final String PAGE = "page";
    static final String SIZE = "size";
    static final String SORT = "sort";

    static final String OUT_PAGE = "page";
    static final String OUT_RESULT = "result";

    /** The users/query enrichment flags security's UserService.fillDetails reads, passed through as given. */
    static final List<String> USER_FETCH_FLAGS = List.of("fetchProfiles", "fetchClients", "fetchManagingClients",
            "fetchCreatedBy", "fetchDesignation", "fetchReportingTo");

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_INACTIVE = "INACTIVE";

    private CallerSecurityFunctions() {
    }

    public static List<AbstractReactiveFunction> all(IFeignSecurityAsCaller asCaller,
            IFeignSecurityService securityService, ObjectMapper mapper, Gson gson) {

        Deps d = new Deps(asCaller, securityService, mapper == null ? new ObjectMapper() : mapper,
                gson == null ? new Gson() : gson);

        return List.of(
                new QueryUsers(d), new GetUser(d), new CreateUser(d), new UpdateUser(d), new DeleteUser(d),
                new SetUserStatus(d), new AssignProfile(d), new RemoveProfile(d),
                new SetClientStatus(d), new UpdateClient(d), new DeleteClient(d),
                new AddClientManager(d), new RemoveClientManager(d), new ReplaceClientManager(d),
                new SetClientManagers(d), new MigrateClientManagers(d),
                new LogClientActivity(d), new QueryClientActivities(d),
                new GetAppProfiles(d));
    }

    record Deps(IFeignSecurityAsCaller asCaller, IFeignSecurityService security, ObjectMapper mapper, Gson gson) {
    }

    /** Who is calling and the request headers to forward on their behalf. */
    record Caller(BigInteger userId, BigInteger clientId, String authorization, String forwardedHost,
            String forwardedPort, String clientCode, String appCode,
            Collection<? extends GrantedAuthority> authorities) {
    }

    /** Spring Security's text for a failed {@code @PreAuthorize}, as security answers it. */
    static final String ACCESS_DENIED = "Access Denied";

    static final String CLIENT_UPDATE = "Authorities.Client_UPDATE";
    static final String CANNOT_UPDATE_USER = "Cannot update user for the selected client";
    static final String USER_STATUS_REFUSED = "selected user doesn't have access to change status code";

    /** Security's client-manager routes refuse an unmanaged (or unknown) user or client with this. */
    static GenericException managerRefusal(String operation, Throwable cause) {
        return new GenericException(HttpStatus.FORBIDDEN, permissionRequired("Client Manager " + operation), cause);
    }

    /** The tenant rule refused: the client or user is out of the caller's reach. */
    static final class OutOfScope extends GenericException {

        private static final long serialVersionUID = 1L;

        OutOfScope(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    /** The user named does not exist (found out while placing it for the tenant rule). */
    static final class UnknownUser extends GenericException {

        private static final long serialVersionUID = 1L;

        final transient BigInteger userId;

        UnknownUser(BigInteger userId, Throwable cause) {
            super(HttpStatus.NOT_FOUND, userNotFound(userId), cause);
            this.userId = userId;
        }
    }

    /** Security's OBJECT_NOT_FOUND text for a user. */
    static String userNotFound(BigInteger userId) {
        return "User with id " + userId + " not found";
    }

    /** Security's FORBIDDEN_PERMISSION text. */
    static String permissionRequired(String what) {
        return "Permission/Role " + what + " is required to use this operation";
    }

    /**
     * The caller to act as, or a 403: outside a definition, with no signed in user, without a token
     * or without the request's forwarded host (no request to act as).
     */
    static Mono<Caller> caller(String functionName) {

        String full = SecurityFunctionSupport.NAME_SPACE + "." + functionName;

        return SecurityFunctionSupport.definitionContextAuthentication(functionName)
                .flatMap(ca -> Mono.deferContextual(cv -> {

                    if (!ca.isAuthenticated() || ca.getUser() == null || ca.getUser().getId() == null
                            || ca.getUser().getClientId() == null)
                        return Mono.error(SecurityFunctionSupport.forbidden(full + " needs a signed in user"));

                    if (StringUtil.safeIsBlank(ca.getAccessToken()))
                        return Mono.error(SecurityFunctionSupport.forbidden(
                                full + " has no caller token to act with"));

                    String host = text(cv.getOrDefault(EPRemoteFunction.FORWARDED_HOST, null));
                    if (StringUtil.safeIsBlank(host))
                        return Mono.error(SecurityFunctionSupport.forbidden(full
                                + " acts only inside a caller's request; this execution (an event, a schedule or an"
                                + " internal call) has no request to act as"));

                    String port = text(cv.getOrDefault(EPRemoteFunction.FORWARDED_PORT, null));

                    return Mono.just(new Caller(ca.getUser().getId(), ca.getUser().getClientId(),
                            ca.getAccessToken(), host, port == null ? "" : port,
                            nonNull(ca.getUrlClientCode(), ca.getClientCode()), nonNull(ca.getUrlAppCode(), ""),
                            ca.getAuthorities()));
                }));
    }

    private static String text(Object o) {
        return o == null ? null : o.toString();
    }

    private static String nonNull(String a, String b) {
        return StringUtil.safeIsBlank(a) ? b : a;
    }

    /**
     * Security's error, with its status and message, instead of a bare feign exception. A JSON
     * body's {@code message} is used, and a plain-text body (Spring Security's
     * {@code Access Denied}) is the message as is, as the page would have read it.
     */
    static Throwable fromFeign(String functionName, FeignException fe) {

        HttpStatus status = fe.status() >= 100 ? HttpStatus.resolve(fe.status()) : null;
        if (status == null)
            status = HttpStatus.BAD_GATEWAY;

        String message = null;
        String text = null;
        try {
            String body = fe.contentUTF8();
            if (!StringUtil.safeIsBlank(body)) {
                text = body.trim();
                if (text.startsWith("{")) {
                    JsonElement e = JsonParser.parseString(text);
                    if (e.isJsonObject() && e.getAsJsonObject().has("message")
                            && !e.getAsJsonObject().get("message").isJsonNull())
                        message = e.getAsJsonObject().get("message").getAsString();
                    text = null;
                }
            }
        } catch (RuntimeException ignored) {
            text = null; // not readable: fall back to the generic text
        }

        if (StringUtil.safeIsBlank(message) && !StringUtil.safeIsBlank(text) && !text.startsWith("<"))
            message = text.length() > 500 ? text.substring(0, 500) : text;

        if (StringUtil.safeIsBlank(message))
            message = SecurityFunctionSupport.NAME_SPACE + "." + functionName + ": security answered "
                    + (fe.status() >= 100 ? fe.status() : "nothing")
                    + (StringUtil.safeIsBlank(text) ? "" : " (" + (text.length() > 200 ? text.substring(0, 200) : text)
                            + ")");

        return new GenericException(status, message, fe);
    }

    /** What every function here shares: signature shape, caller, tenant checks, JSON in and out. */
    abstract static class Base extends AbstractReactiveFunction {

        protected final Deps d;

        Base(Deps d) {
            this.d = d;
        }

        abstract String name();

        abstract Map<String, Parameter> parameters();

        abstract String outputName();

        abstract Schema outputSchema();

        abstract Mono<JsonElement> act(Map<String, JsonElement> args, Caller c);

        @Override
        public FunctionSignature getSignature() {

            Event event = new Event()
                    .setName(Event.OUTPUT)
                    .setParameters(Map.of(this.outputName(), this.outputSchema()));

            return new FunctionSignature()
                    .setNamespace(SecurityFunctionSupport.NAME_SPACE)
                    .setName(this.name())
                    .setParameters(this.parameters())
                    .setEvents(Map.of(event.getName(), event));
        }

        @Override
        protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

            Map<String, JsonElement> args = context.getArguments() == null ? Map.of() : context.getArguments();

            return caller(this.name())
                    .flatMap(c -> Mono.defer(() -> this.act(args, c))
                            .onErrorMap(FeignException.class, fe -> fromFeign(this.name(), fe))
                            .onErrorMap(e -> e instanceof OutOfScope || e instanceof UnknownUser,
                                    e -> this.asSecurityWould(args, c, e)))
                    .map(out -> new FunctionOutput(List.of(EventResult.outputOf(
                            Map.of(this.outputName(), out == null ? JsonNull.INSTANCE : out)))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "CoreServices.Security." + this.name()));
        }

        // ------------------------------------------- refusals, worded as security

        /**
         * The authorities the security route checks before anything else (its
         * {@code @PreAuthorize hasAuthority(..) and ..}). Used only to word a refusal.
         */
        List<String> routeAuthorities() {
            return List.of();
        }

        /** How security words its refusal of an out-of-reach target on this route. Default: the tenant rule's text. */
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return e;
        }

        /** How security answers an unknown user on this route. Default: 404 "User with id X not found". */
        GenericException securityNotFound(Map<String, JsonElement> args, UnknownUser e) {
            return e;
        }

        /**
         * A refusal the tenant rule (or the user placing) made before anything was sent, worded as
         * security words the same refusal: 403 Access Denied when the caller lacks the route's
         * authority, else the route's own text. The decision itself never changes here.
         */
        Throwable asSecurityWould(Map<String, JsonElement> args, Caller c, Throwable e) {

            for (String authority : this.routeAuthorities())
                if (!SecurityContextUtil.hasAuthority(authority, c.authorities()))
                    return new GenericException(HttpStatus.FORBIDDEN, ACCESS_DENIED, e);

            if (e instanceof OutOfScope out)
                return this.securityRefusal(args, out);

            return this.securityNotFound(args, (UnknownUser) e);
        }

        // ------------------------------------------------------------ tenant

        /** Own client or one the caller's client manages; anything else 403. */
        Mono<Boolean> requireOwnOrManaged(Caller c, BigInteger targetClientId) {

            if (targetClientId == null)
                return Mono.error(SecurityFunctionSupport.badRequest(CLIENT_ID + " is required"));

            if (targetClientId.equals(c.clientId()))
                return Mono.just(Boolean.TRUE);

            return this.requireManaged(c, targetClientId);
        }

        /** Strictly below the caller's client: the caller's own client is refused too. */
        Mono<Boolean> requireManaged(Caller c, BigInteger targetClientId) {

            if (targetClientId == null)
                return Mono.error(SecurityFunctionSupport.badRequest(CLIENT_ID + " is required"));

            if (targetClientId.equals(c.clientId()))
                return Mono.error(SecurityFunctionSupport.forbidden(SecurityFunctionSupport.NAME_SPACE + "."
                        + this.name() + " does not act on the caller's own client " + targetClientId));

            return this.d.security().doesClientManageClient(c.clientId(), targetClientId)
                    .defaultIfEmpty(Boolean.FALSE)
                    .onErrorReturn(Boolean.FALSE)
                    .flatMap(managed -> Boolean.TRUE.equals(managed) ? Mono.just(Boolean.TRUE)
                            : Mono.error(new OutOfScope(SecurityFunctionSupport.NAME_SPACE + "."
                                    + this.name() + ": client " + targetClientId
                                    + " is neither the caller's client nor one it manages")));
        }

        /**
         * For reading one user only: own client, a managed client, or the caller's client's direct
         * managing client when it holds the app ({@link SecurityFunctionSupport#isRelatedClient}).
         */
        Mono<Boolean> requireReadable(Caller c, BigInteger targetClientId) {

            if (targetClientId == null || targetClientId.equals(c.clientId()))
                return Mono.just(Boolean.TRUE);

            return SecurityFunctionSupport.isRelatedClient(this.d.security(), c.clientId(), targetClientId,
                    c.appCode())
                    .defaultIfEmpty(Boolean.FALSE)
                    .flatMap(related -> Boolean.TRUE.equals(related) ? Mono.just(Boolean.TRUE)
                            : Mono.error(new OutOfScope(SecurityFunctionSupport.NAME_SPACE + "." + this.name()
                                    + ": client " + targetClientId + " is not the caller's client, one it manages"
                                    + " or the client that manages it")));
        }

        /** The client a user belongs to, read only to place the user for the tenant rule. */
        Mono<BigInteger> userClientId(BigInteger userId) {
            return this.d.security().getUserInternal(userId, new LinkedMultiValueMap<>())
                    .onErrorMap(FeignException.class, fe -> fe.status() == 404 ? new UnknownUser(userId, fe) : fe)
                    .map(User::getClientId)
                    .switchIfEmpty(Mono.error(() -> new UnknownUser(userId, null)));
        }

        /** The user is in the caller's client or a client it manages. The caller themself always is. */
        Mono<Boolean> requireUserInScope(Caller c, BigInteger userId) {
            if (userId.equals(c.userId()))
                return Mono.just(Boolean.TRUE);
            return this.userClientId(userId).flatMap(clientId -> this.requireOwnOrManaged(c, clientId));
        }

        Mono<Boolean> requireUsersInScope(Caller c, List<BigInteger> userIds) {
            return Flux.fromIterable(userIds)
                    .concatMap(id -> this.requireUserInScope(c, id))
                    .then(Mono.just(Boolean.TRUE));
        }

        // -------------------------------------------------------------- json

        JsonNode node(JsonElement element) {
            try {
                return this.d.mapper().readTree(this.d.gson().toJson(element == null ? JsonNull.INSTANCE : element));
            } catch (JsonProcessingException ex) {
                throw SecurityFunctionSupport.badRequest("Not JSON : " + ex.getOriginalMessage());
            }
        }

        static JsonElement parse(String body) {
            if (StringUtil.safeIsBlank(body))
                return JsonNull.INSTANCE;
            return JsonParser.parseString(body);
        }

        // -------------------------------------------------------------- args

        static BigInteger id(Map<String, JsonElement> args, String name) {
            return SecurityFunctionSupport.idOf(name, args.get(name));
        }

        static BigInteger requiredId(Map<String, JsonElement> args, String name) {
            BigInteger id = id(args, name);
            if (id == null)
                throw SecurityFunctionSupport.badRequest(name + " is required");
            return id;
        }

        static JsonObject requiredObject(Map<String, JsonElement> args, String name) {
            JsonElement e = args.get(name);
            if (e == null || !e.isJsonObject())
                throw SecurityFunctionSupport.badRequest(name + " must be an object");
            return e.getAsJsonObject();
        }

        static boolean bool(Map<String, JsonElement> args, String name, boolean def) {
            JsonElement e = args.get(name);
            if (e == null || e.isJsonNull() || !e.isJsonPrimitive())
                return def;
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean())
                return p.getAsBoolean();
            if (p.isString())
                return "true".equalsIgnoreCase(p.getAsString().trim());
            return def;
        }

        static String string(Map<String, JsonElement> args, String name) {
            JsonElement e = args.get(name);
            if (e == null || e.isJsonNull() || !e.isJsonPrimitive())
                return null;
            String s = e.getAsString().trim();
            return s.isEmpty() ? null : s;
        }

        /** The client id inside a body (clientId), or null when the body does not name one. */
        static BigInteger bodyClientId(JsonObject body) {
            return SecurityFunctionSupport.idOf(CLIENT_ID, body.get(CLIENT_ID));
        }

        static String status(Map<String, JsonElement> args) {
            String s = string(args, STATUS);
            String normalised = s == null ? "" : s.toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "");
            if (STATUS_ACTIVE.equals(normalised))
                return STATUS_ACTIVE;
            if (STATUS_INACTIVE.equals(normalised))
                return STATUS_INACTIVE;
            throw SecurityFunctionSupport.badRequest(STATUS + " must be ACTIVE or INACTIVE");
        }
    }

    // ------------------------------------------------------------ schemas

    static Parameter idParam(String name) {
        return Parameter.of(name, SecurityFunctionSupport.idSchema(name));
    }

    static Parameter boolParam(String name, boolean def) {
        return Parameter.of(name, Schema.ofBoolean(name).setDefaultValue(new JsonPrimitive(def)));
    }

    static Parameter objectParam(String name) {
        return Parameter.of(name, Schema.ofObject(name));
    }

    static Parameter optionalObjectParam(String name) {
        return Parameter.of(name, Schema.ofObject(name).setDefaultValue(new JsonObject()));
    }

    static Parameter optionalStringParam(String name) {
        return Parameter.of(name, Schema.ofString(name).setDefaultValue(new JsonPrimitive("")));
    }

    // ================================================================ users

    /**
     * {@code POST /users/query}: the Query the pages send (page, size, sort, condition), plus the
     * same enrichment flags. Security scopes the page to what the caller may see; here, every
     * {@code clientId} a (non negated) filter names, and the optional {@code clientId} argument
     * (ANDed onto the condition), must be the caller's client or one it manages.
     *
     * <p>Output {@code page}: security's page as is ({@code content}, {@code totalElements},
     * {@code totalPages}, {@code number}, {@code size}, ...).
     */
    public static final class QueryUsers extends Base {

        QueryUsers(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "QueryUsers";
        }

        @Override
        Map<String, Parameter> parameters() {
            Map<String, Parameter> p = new HashMap<>();
            p.put(QUERY, optionalObjectParam(QUERY));
            p.put(CLIENT_ID, idParam(CLIENT_ID));
            p.put(APP_CODE, optionalStringParam(APP_CODE));
            p.put(APP_ID, idParam(APP_ID));
            USER_FETCH_FLAGS.forEach(f -> p.put(f, boolParam(f, false)));
            return p;
        }

        @Override
        String outputName() {
            return OUT_PAGE;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(OUT_PAGE);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {

            JsonElement given = args.get(QUERY);
            JsonObject query = given != null && given.isJsonObject() ? given.getAsJsonObject().deepCopy()
                    : new JsonObject();

            BigInteger clientId = id(args, CLIENT_ID);

            Set<BigInteger> named = new LinkedHashSet<>();
            if (query.has("condition"))
                conditionClientIds(query.get("condition"), named);
            if (clientId != null) {
                named.add(clientId);
                query.add("condition", andClient(query.get("condition"), clientId));
            }

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            USER_FETCH_FLAGS.forEach(f -> {
                if (bool(args, f, false))
                    params.add(f, "true");
            });
            String appCode = string(args, APP_CODE);
            if (appCode != null)
                params.add(APP_CODE, appCode);
            BigInteger appId = id(args, APP_ID);
            if (appId != null)
                params.add(APP_ID, appId.toString());

            JsonNode body = this.node(query);

            // Security first (a read), so its own refusal keeps its text; then the tenant rule on
            // the clients the query named.
            return Mono.defer(() -> this.d.asCaller().queryUsers(c.authorization(), c.forwardedHost(),
                    c.forwardedPort(), c.clientCode(), c.appCode(), params, body))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE)
                    .flatMap(page -> Flux.fromIterable(named)
                            .concatMap(id -> this.requireOwnOrManaged(c, id))
                            .then(Mono.just(page)));
        }

        /** {@code clientId = X} ANDed onto whatever condition was given (none, a filter or a group). */
        static JsonObject andClient(JsonElement condition, BigInteger clientId) {

            JsonObject filter = new JsonObject();
            filter.addProperty("field", CLIENT_ID);
            filter.addProperty("operator", "EQUALS");
            filter.add("value", new JsonPrimitive(clientId));

            if (condition == null || condition.isJsonNull()
                    || (condition.isJsonObject() && condition.getAsJsonObject().size() == 0))
                return filter;

            JsonObject and = new JsonObject();
            and.addProperty("operator", "AND");
            JsonArray conditions = new JsonArray();
            conditions.add(condition);
            conditions.add(filter);
            and.add("conditions", conditions);
            return and;
        }

        /**
         * Every client id a non negated filter on {@code clientId} names, through nested groups. A
         * value that is a field reference ({@code isValueField}) names no client.
         */
        static void conditionClientIds(JsonElement condition, Set<BigInteger> into) {

            if (condition == null || !condition.isJsonObject())
                return;

            JsonObject o = condition.getAsJsonObject();

            if (o.has("conditions") && o.get("conditions").isJsonArray()) {
                if (isTrue(o.get("negate")))
                    return;
                for (JsonElement each : o.getAsJsonArray("conditions"))
                    conditionClientIds(each, into);
                return;
            }

            if (!o.has("field") || !o.get("field").isJsonPrimitive()
                    || !CLIENT_ID.equalsIgnoreCase(o.get("field").getAsString().replace("_", ""))
                    || isTrue(o.get("negate")))
                return;

            if (!isTrue(o.get("isValueField")))
                addIds(o.get("value"), into);
            if (!isTrue(o.get("isToValueField")))
                addIds(o.get("toValue"), into);
            addIds(o.get("multiValue"), into);
        }

        private static boolean isTrue(JsonElement e) {
            return e != null && e.isJsonPrimitive() && "true".equalsIgnoreCase(e.getAsString());
        }

        private static void addIds(JsonElement value, Set<BigInteger> into) {

            if (value == null || value.isJsonNull())
                return;

            if (value.isJsonArray()) {
                for (JsonElement each : value.getAsJsonArray())
                    addIds(each, into);
                return;
            }

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && value.getAsString().contains(",")) {
                for (String part : value.getAsString().split(","))
                    addIds(new JsonPrimitive(part.trim()), into);
                return;
            }

            BigInteger id = SecurityFunctionSupport.idOf(CLIENT_ID, value);
            if (id != null)
                into.add(id);
        }
    }

    /**
     * {@code GET /users/{id}}. Output {@code user}. Security answers first (its User READ check and
     * its 404 keep their text); the user must then be the caller, in the caller's client, a client
     * it manages, or the caller's client's direct managing client (a partner reading the managing
     * client's user its deal is assigned to).
     */
    public static final class GetUser extends Base {

        GetUser(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "GetUser";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(USER_ID, idParam(USER_ID));
        }

        @Override
        String outputName() {
            return USER;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(USER);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger userId = requiredId(args, USER_ID);
            return Mono.defer(() -> this.d.asCaller().getUser(c.authorization(), c.forwardedHost(),
                    c.forwardedPort(), c.clientCode(), c.appCode(), userId))
                    .map(Base::parse)
                    .flatMap(user -> userId.equals(c.userId()) ? Mono.just(user)
                            : this.clientOf(user, userId)
                                    .flatMap(clientId -> this.requireReadable(c, clientId))
                                    .thenReturn(user))
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }

        /** The user's client from security's answer, else placed through the internal read. */
        private Mono<BigInteger> clientOf(JsonElement user, BigInteger userId) {
            BigInteger clientId = user.isJsonObject()
                    ? SecurityFunctionSupport.idOf(CLIENT_ID, user.getAsJsonObject().get(CLIENT_ID))
                    : null;
            return clientId != null ? Mono.just(clientId) : this.userClientId(userId);
        }
    }

    /**
     * {@code POST /users}: creates the user. The user's {@code clientId} (the caller's client when
     * absent, as security defaults it) must be the caller's or a managed client. Output {@code user}.
     */
    public static final class CreateUser extends Base {

        CreateUser(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "CreateUser";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of("Authorities.User_CREATE");
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, "Cannot create User for the selected client", e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(USER, objectParam(USER));
        }

        @Override
        String outputName() {
            return USER;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(USER);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            JsonObject user = requiredObject(args, USER);
            BigInteger clientId = bodyClientId(user);
            JsonNode body = this.node(user);
            return this.requireOwnOrManaged(c, clientId == null ? c.clientId() : clientId)
                    .then(Mono.defer(() -> this.d.asCaller().createUser(c.authorization(), c.forwardedHost(),
                            c.forwardedPort(), c.clientCode(), c.appCode(), body)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /**
     * Updates a user: {@code partial} (default true) is {@code PATCH /users/{id}} with only the
     * fields given; {@code partial: false} is {@code PUT /users/{id}} with the whole user, as the
     * partner pages send it. The user, and a client the body moves it to, must be in scope.
     * Output {@code user}.
     */
    public static final class UpdateUser extends Base {

        UpdateUser(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "UpdateUser";
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, CANNOT_UPDATE_USER, e);
        }

        /** PUT reads the user first (404); PATCH folds a missing user into its update refusal (403). */
        @Override
        GenericException securityNotFound(Map<String, JsonElement> args, UnknownUser e) {
            return bool(args, PARTIAL, true) ? new GenericException(HttpStatus.FORBIDDEN, CANNOT_UPDATE_USER, e) : e;
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(USER_ID, idParam(USER_ID), USER, objectParam(USER), PARTIAL, boolParam(PARTIAL, true));
        }

        @Override
        String outputName() {
            return USER;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(USER);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {

            JsonObject user = requiredObject(args, USER);
            BigInteger userId = id(args, USER_ID);
            if (userId == null)
                userId = SecurityFunctionSupport.idOf("user.id", user.get("id"));
            if (userId == null)
                throw SecurityFunctionSupport.badRequest(USER_ID + " is required");

            final BigInteger id = userId;
            boolean partial = bool(args, PARTIAL, true);
            BigInteger movesTo = bodyClientId(user);
            JsonNode body = this.node(user);

            return this.userClientId(id)
                    .flatMap(current -> this.requireOwnOrManaged(c, current)
                            .then(Mono.defer(() -> movesTo == null || movesTo.equals(current)
                                    ? Mono.just(Boolean.TRUE)
                                    : this.requireOwnOrManaged(c, movesTo))))
                    .then(Mono.defer(() -> partial
                            ? this.d.asCaller().patchUser(c.authorization(), c.forwardedHost(), c.forwardedPort(),
                                    c.clientCode(), c.appCode(), id, body)
                            : this.d.asCaller().putUser(c.authorization(), c.forwardedHost(), c.forwardedPort(),
                                    c.clientCode(), c.appCode(), id, body)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /** {@code DELETE /users/{id}}. Output {@code deleted}: true once security accepted it. */
    public static final class DeleteUser extends Base {

        DeleteUser(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "DeleteUser";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of("Authorities.User_DELETE");
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, CANNOT_UPDATE_USER, e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(USER_ID, idParam(USER_ID));
        }

        @Override
        String outputName() {
            return "deleted";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean("deleted");
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger userId = requiredId(args, USER_ID);
            return this.requireUserInScope(c, userId)
                    .then(Mono.defer(() -> this.d.asCaller().deleteUser(c.authorization(), c.forwardedHost(),
                            c.forwardedPort(), c.clientCode(), c.appCode(), userId)))
                    .map(CallerSecurityFunctions::acceptedCount)
                    .defaultIfEmpty(new JsonPrimitive(true));
        }
    }

    /**
     * A delete answers 204 with no body, or a count: true unless security said it deleted 0 rows
     * (or answered false).
     */
    static JsonElement acceptedCount(String body) {
        JsonElement e = Base.parse(body);
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean())
                return p;
            if (p.isNumber())
                return new JsonPrimitive(p.getAsBigDecimal().signum() != 0);
        }
        return new JsonPrimitive(true);
    }

    /**
     * {@code PATCH /users/makeUserActive|makeUserInActive?userId}. {@code status}: ACTIVE or
     * INACTIVE. Output {@code result}: security's boolean.
     */
    public static final class SetUserStatus extends Base {

        SetUserStatus(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "SetUserStatus";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of("Authorities.User_UPDATE");
        }

        /** Security reads the user and refuses both an unmanaged and a missing one with this text. */
        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, USER_STATUS_REFUSED, e);
        }

        @Override
        GenericException securityNotFound(Map<String, JsonElement> args, UnknownUser e) {
            return new GenericException(HttpStatus.FORBIDDEN, USER_STATUS_REFUSED, e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(USER_ID, idParam(USER_ID), STATUS, Parameter.of(STATUS, Schema.ofString(STATUS)));
        }

        @Override
        String outputName() {
            return OUT_RESULT;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean(OUT_RESULT);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger userId = requiredId(args, USER_ID);
            String status = status(args);
            return this.requireUserInScope(c, userId)
                    .then(Mono.defer(() -> STATUS_ACTIVE.equals(status)
                            ? this.d.asCaller().makeUserActive(c.authorization(), c.forwardedHost(),
                                    c.forwardedPort(), c.clientCode(), c.appCode(), userId)
                            : this.d.asCaller().makeUserInActive(c.authorization(), c.forwardedHost(),
                                    c.forwardedPort(), c.clientCode(), c.appCode(), userId)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    abstract static class ProfileBase extends Base {

        ProfileBase(Deps d) {
            super(d);
        }

        abstract boolean assign();

        @Override
        List<String> routeAuthorities() {
            return List.of("Authorities.User_UPDATE", "Authorities.Profile_READ");
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN,
                    id(args, PROFILE_ID) + " profile forbidden for the " + id(args, USER_ID) + " user", e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(USER_ID, idParam(USER_ID), PROFILE_ID, idParam(PROFILE_ID));
        }

        @Override
        String outputName() {
            return OUT_RESULT;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean(OUT_RESULT);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger userId = requiredId(args, USER_ID);
            BigInteger profileId = requiredId(args, PROFILE_ID);
            return this.requireUserInScope(c, userId)
                    .then(Mono.defer(() -> this.assign()
                            ? this.d.asCaller().assignProfile(c.authorization(), c.forwardedHost(),
                                    c.forwardedPort(), c.clientCode(), c.appCode(), userId, profileId)
                            : this.d.asCaller().removeProfile(c.authorization(), c.forwardedHost(),
                                    c.forwardedPort(), c.clientCode(), c.appCode(), userId, profileId)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /** {@code GET /users/{userId}/assignProfile/{profileId}}. Output {@code result}. */
    public static final class AssignProfile extends ProfileBase {

        AssignProfile(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "AssignProfile";
        }

        @Override
        boolean assign() {
            return true;
        }
    }

    /** {@code GET /users/{userId}/removeProfile/{profileId}}. Output {@code result}. */
    public static final class RemoveProfile extends ProfileBase {

        RemoveProfile(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "RemoveProfile";
        }

        @Override
        boolean assign() {
            return false;
        }
    }

    // ============================================================== clients

    /**
     * {@code GET /clients/makeClientActive|makeClientInActive?clientId}. A MANAGED client only.
     * Output {@code result}.
     */
    public static final class SetClientStatus extends Base {

        SetClientStatus(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "SetClientStatus";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of(CLIENT_UPDATE);
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, "selected client doesn't have access to change status code",
                    e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(CLIENT_ID, idParam(CLIENT_ID), STATUS, Parameter.of(STATUS, Schema.ofString(STATUS)));
        }

        @Override
        String outputName() {
            return OUT_RESULT;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean(OUT_RESULT);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger clientId = requiredId(args, CLIENT_ID);
            String status = status(args);
            return this.requireManaged(c, clientId)
                    .then(Mono.defer(() -> STATUS_ACTIVE.equals(status)
                            ? this.d.asCaller().makeClientActive(c.authorization(), c.forwardedHost(),
                                    c.forwardedPort(), c.clientCode(), c.appCode(), clientId)
                            : this.d.asCaller().makeClientInActive(c.authorization(), c.forwardedHost(),
                                    c.forwardedPort(), c.clientCode(), c.appCode(), clientId)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /**
     * Updates a client: {@code partial} (default true) is {@code PATCH /clients/{id}}, {@code false}
     * is {@code PUT /clients/{id}} with the whole client (what the partner pages send). Own or
     * managed client. Output {@code client}.
     */
    public static final class UpdateClient extends Base {

        UpdateClient(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "UpdateClient";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of(CLIENT_UPDATE);
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, permissionRequired("Client UPDATE"), e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(CLIENT_ID, idParam(CLIENT_ID), CLIENT, objectParam(CLIENT), PARTIAL,
                    boolParam(PARTIAL, true));
        }

        @Override
        String outputName() {
            return CLIENT;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(CLIENT);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {

            JsonObject client = requiredObject(args, CLIENT);
            BigInteger clientId = id(args, CLIENT_ID);
            if (clientId == null)
                clientId = SecurityFunctionSupport.idOf("client.id", client.get("id"));
            if (clientId == null)
                throw SecurityFunctionSupport.badRequest(CLIENT_ID + " is required");

            final BigInteger id = clientId;
            boolean partial = bool(args, PARTIAL, true);
            JsonNode body = this.node(client);

            return this.requireOwnOrManaged(c, id)
                    .then(Mono.defer(() -> partial
                            ? this.d.asCaller().patchClient(c.authorization(), c.forwardedHost(),
                                    c.forwardedPort(), c.clientCode(), c.appCode(), id, body)
                            : this.d.asCaller().putClient(c.authorization(), c.forwardedHost(), c.forwardedPort(),
                                    c.clientCode(), c.appCode(), id, body)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /** {@code DELETE /clients/{id}} (security marks it DELETED). A MANAGED client only. Output {@code deleted}. */
    public static final class DeleteClient extends Base {

        DeleteClient(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "DeleteClient";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of("Authorities.Client_DELETE");
        }

        /** Security's delete reads the client first, refusing an unmanaged one as a Client READ. */
        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, permissionRequired("Client READ"), e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(CLIENT_ID, idParam(CLIENT_ID));
        }

        @Override
        String outputName() {
            return "deleted";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean("deleted");
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger clientId = requiredId(args, CLIENT_ID);
            return this.requireManaged(c, clientId)
                    .then(Mono.defer(() -> this.d.asCaller().deleteClient(c.authorization(), c.forwardedHost(),
                            c.forwardedPort(), c.clientCode(), c.appCode(), clientId)))
                    .map(CallerSecurityFunctions::acceptedCount)
                    .defaultIfEmpty(new JsonPrimitive(true));
        }
    }

    // ======================================================= client managers

    abstract static class ManagerPairBase extends Base {

        ManagerPairBase(Deps d) {
            super(d);
        }

        abstract Mono<String> call(Caller c, BigInteger userId, BigInteger clientId);

        /** The word in security's refusal: CREATE or DELETE. */
        abstract String operation();

        @Override
        List<String> routeAuthorities() {
            return List.of(CLIENT_UPDATE);
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return managerRefusal(this.operation(), e);
        }

        @Override
        GenericException securityNotFound(Map<String, JsonElement> args, UnknownUser e) {
            return managerRefusal(this.operation(), e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(USER_ID, idParam(USER_ID), CLIENT_ID, idParam(CLIENT_ID));
        }

        @Override
        String outputName() {
            return OUT_RESULT;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean(OUT_RESULT);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger userId = requiredId(args, USER_ID);
            BigInteger clientId = requiredId(args, CLIENT_ID);
            return this.requireOwnOrManaged(c, clientId)
                    .then(Mono.defer(() -> this.requireUserInScope(c, userId)))
                    .then(Mono.defer(() -> this.call(c, userId, clientId)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /** {@code POST /client-managers/{userId}/{clientId}}: the user becomes a manager of the client. */
    public static final class AddClientManager extends ManagerPairBase {

        AddClientManager(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "AddClientManager";
        }

        @Override
        String operation() {
            return "CREATE";
        }

        @Override
        Mono<String> call(Caller c, BigInteger userId, BigInteger clientId) {
            return this.d.asCaller().addClientManager(c.authorization(), c.forwardedHost(), c.forwardedPort(),
                    c.clientCode(), c.appCode(), userId, clientId);
        }
    }

    /** {@code DELETE /client-managers/{userId}/{clientId}}. */
    public static final class RemoveClientManager extends ManagerPairBase {

        RemoveClientManager(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "RemoveClientManager";
        }

        @Override
        String operation() {
            return "DELETE";
        }

        @Override
        Mono<String> call(Caller c, BigInteger userId, BigInteger clientId) {
            return this.d.asCaller().removeClientManager(c.authorization(), c.forwardedHost(), c.forwardedPort(),
                    c.clientCode(), c.appCode(), userId, clientId);
        }
    }

    /**
     * {@code POST /client-managers/{clientId}?oldManagerId&newManagerId}: replaces one manager of the
     * client (or adds one when {@code oldManagerId} is not given). Output {@code result}.
     */
    public static final class ReplaceClientManager extends Base {

        ReplaceClientManager(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "ReplaceClientManager";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of(CLIENT_UPDATE);
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return managerRefusal("UPDATE", e);
        }

        @Override
        GenericException securityNotFound(Map<String, JsonElement> args, UnknownUser e) {
            return managerRefusal("UPDATE", e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(CLIENT_ID, idParam(CLIENT_ID), NEW_MANAGER_ID, idParam(NEW_MANAGER_ID), OLD_MANAGER_ID,
                    idParam(OLD_MANAGER_ID));
        }

        @Override
        String outputName() {
            return OUT_RESULT;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean(OUT_RESULT);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger clientId = requiredId(args, CLIENT_ID);
            BigInteger newManagerId = requiredId(args, NEW_MANAGER_ID);
            BigInteger oldManagerId = id(args, OLD_MANAGER_ID);

            List<BigInteger> users = new ArrayList<>();
            users.add(newManagerId);
            if (oldManagerId != null)
                users.add(oldManagerId);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            if (oldManagerId != null)
                params.add(OLD_MANAGER_ID, oldManagerId.toString());
            params.add(NEW_MANAGER_ID, newManagerId.toString());

            return this.requireOwnOrManaged(c, clientId)
                    .then(Mono.defer(() -> this.requireUsersInScope(c, users)))
                    .then(Mono.defer(() -> this.d.asCaller().replaceClientManager(c.authorization(),
                            c.forwardedHost(), c.forwardedPort(), c.clientCode(), c.appCode(), clientId, params)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /**
     * {@code PUT /client-managers/{clientId}/managers}: the client's managers become exactly
     * {@code managerIds}. Every manager must be in scope. Output {@code result}.
     */
    public static final class SetClientManagers extends Base {

        SetClientManagers(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "SetClientManagers";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of(CLIENT_UPDATE);
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return managerRefusal("SYNC", e);
        }

        @Override
        GenericException securityNotFound(Map<String, JsonElement> args, UnknownUser e) {
            return managerRefusal("SYNC", e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(CLIENT_ID, idParam(CLIENT_ID), MANAGER_IDS,
                    Parameter.of(MANAGER_IDS, SecurityFunctionSupport.idArraySchema(MANAGER_IDS)));
        }

        @Override
        String outputName() {
            return OUT_RESULT;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean(OUT_RESULT);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger clientId = requiredId(args, CLIENT_ID);
            List<BigInteger> managerIds = SecurityFunctionSupport.idsOf(MANAGER_IDS, args.get(MANAGER_IDS));
            JsonNode body = this.node(SecurityFunctionSupport.toJsonArray(managerIds));

            return this.requireOwnOrManaged(c, clientId)
                    .then(Mono.defer(() -> this.requireUsersInScope(c, managerIds)))
                    .then(Mono.defer(() -> this.d.asCaller().setClientManagers(c.authorization(),
                            c.forwardedHost(), c.forwardedPort(), c.clientCode(), c.appCode(), clientId, body)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /**
     * {@code POST /client-managers/migrateTo/{toUserId}?fromUid&removeManager}: moves every client
     * {@code fromUserId} manages to {@code toUserId}. Both users must be in scope. Output
     * {@code count}: how many clients moved, as security answers it.
     */
    public static final class MigrateClientManagers extends Base {

        MigrateClientManagers(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "MigrateClientManagers";
        }

        @Override
        List<String> routeAuthorities() {
            return List.of(CLIENT_UPDATE);
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return managerRefusal("MIGRATE", e);
        }

        @Override
        GenericException securityNotFound(Map<String, JsonElement> args, UnknownUser e) {
            return managerRefusal("MIGRATE", e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(FROM_USER_ID, idParam(FROM_USER_ID), TO_USER_ID, idParam(TO_USER_ID), REMOVE_MANAGER,
                    boolParam(REMOVE_MANAGER, true));
        }

        @Override
        String outputName() {
            return "count";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofInteger("count");
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger from = requiredId(args, FROM_USER_ID);
            BigInteger to = requiredId(args, TO_USER_ID);
            boolean removeManager = bool(args, REMOVE_MANAGER, true);

            return this.requireUsersInScope(c, List.of(from, to))
                    .then(Mono.defer(() -> this.d.asCaller().migrateClientManagers(c.authorization(),
                            c.forwardedHost(), c.forwardedPort(), c.clientCode(), c.appCode(), to, from,
                            removeManager)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    // ===================================================== client activities

    /**
     * {@code POST /client-activities}: {@code activity} is what the pages post ({@code clientId},
     * {@code activityName}, {@code description}); without a clientId it is the caller's client, as
     * security defaults it. Output {@code activity}: the stored row.
     */
    public static final class LogClientActivity extends Base {

        LogClientActivity(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "LogClientActivity";
        }

        @Override
        GenericException securityRefusal(Map<String, JsonElement> args, OutOfScope e) {
            return new GenericException(HttpStatus.FORBIDDEN, permissionRequired("Client Activity"), e);
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(ACTIVITY, objectParam(ACTIVITY));
        }

        @Override
        String outputName() {
            return ACTIVITY;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(ACTIVITY);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            JsonObject activity = requiredObject(args, ACTIVITY);

            // Security checks the name before anything else (NAME_MANDATORY), so this comes first too.
            JsonElement name = activity.get("activityName");
            if (name == null || name.isJsonNull() || !name.isJsonPrimitive() || name.getAsString().isBlank())
                throw SecurityFunctionSupport.badRequest("Activity name is required");

            BigInteger clientId = bodyClientId(activity);
            JsonNode body = this.node(activity);
            return this.requireOwnOrManaged(c, clientId == null ? c.clientId() : clientId)
                    .then(Mono.defer(() -> this.d.asCaller().createClientActivity(c.authorization(),
                            c.forwardedHost(), c.forwardedPort(), c.clientCode(), c.appCode(), body)))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }
    }

    /** {@code POST /client-activities/{clientId}/query}: one page of a client's activities. Output {@code page}. */
    public static final class QueryClientActivities extends Base {

        QueryClientActivities(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "QueryClientActivities";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(CLIENT_ID, idParam(CLIENT_ID), QUERY, optionalObjectParam(QUERY));
        }

        @Override
        String outputName() {
            return OUT_PAGE;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(OUT_PAGE);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {
            BigInteger clientId = requiredId(args, CLIENT_ID);
            JsonElement given = args.get(QUERY);
            JsonNode body = this.node(given != null && given.isJsonObject() ? given : new JsonObject());
            // Security first (a read), so its own refusal keeps its text; then the tenant rule.
            return Mono.defer(() -> this.d.asCaller().queryClientActivities(c.authorization(),
                    c.forwardedHost(), c.forwardedPort(), c.clientCode(), c.appCode(), clientId, body))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE)
                    .flatMap(page -> this.requireOwnOrManaged(c, clientId).thenReturn(page));
        }
    }

    // ================================================================= apps

    /**
     * {@code GET /app/{appId}/profiles}: the profiles of an app, as the caller may see them. The app
     * is {@code appId}, else {@code appCode}, else the request's app, resolved through
     * {@code GET /applications/appCode/{appCode}} as the caller (what the partner pages do first).
     * {@code page}, {@code size}, {@code sort} pass through only when given (security's defaults
     * otherwise). Output {@code page}.
     */
    public static final class GetAppProfiles extends Base {

        GetAppProfiles(Deps d) {
            super(d);
        }

        @Override
        String name() {
            return "GetAppProfiles";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(
                    APP_ID, idParam(APP_ID),
                    APP_CODE, optionalStringParam(APP_CODE),
                    PAGE, Parameter.of(PAGE, Schema.ofInteger(PAGE).setDefaultValue(new JsonPrimitive(-1))),
                    SIZE, Parameter.of(SIZE, Schema.ofInteger(SIZE).setDefaultValue(new JsonPrimitive(-1))),
                    SORT, optionalStringParam(SORT));
        }

        @Override
        String outputName() {
            return OUT_PAGE;
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject(OUT_PAGE);
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, Caller c) {

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            int page = intArg(args, PAGE);
            int size = intArg(args, SIZE);
            if (page >= 0)
                params.add(PAGE, String.valueOf(page));
            if (size > 0)
                params.add(SIZE, String.valueOf(size));
            String sort = string(args, SORT);
            if (sort != null)
                params.add(SORT, sort);

            BigInteger appId = id(args, APP_ID);
            String appCode = string(args, APP_CODE);

            Mono<BigInteger> idMono = appId != null ? Mono.just(appId)
                    : this.appIdOf(c, appCode != null ? appCode : c.appCode());

            return idMono
                    .flatMap(id -> this.d.asCaller().getAppProfiles(c.authorization(), c.forwardedHost(),
                            c.forwardedPort(), c.clientCode(), c.appCode(), id, params))
                    .map(Base::parse)
                    .defaultIfEmpty(JsonNull.INSTANCE);
        }

        private Mono<BigInteger> appIdOf(Caller c, String appCode) {

            if (StringUtil.safeIsBlank(appCode))
                return Mono.error(SecurityFunctionSupport.badRequest(APP_ID + " or " + APP_CODE + " is required"));

            return this.d.asCaller().getAppByCode(c.authorization(), c.forwardedHost(), c.forwardedPort(),
                    c.clientCode(), c.appCode(), appCode)
                    .map(Base::parse)
                    .flatMap(app -> {
                        BigInteger id = app.isJsonObject()
                                ? SecurityFunctionSupport.idOf(APP_ID, app.getAsJsonObject().get("id"))
                                : null;
                        return id == null
                                ? Mono.<BigInteger>error(new GenericException(HttpStatus.NOT_FOUND,
                                        "No app found with code : " + appCode))
                                : Mono.just(id);
                    })
                    .switchIfEmpty(Mono.error(() -> new GenericException(HttpStatus.NOT_FOUND,
                            "No app found with code : " + appCode)));
        }

        private static int intArg(Map<String, JsonElement> args, String name) {
            JsonElement e = args.get(name);
            if (e == null || e.isJsonNull() || !e.isJsonPrimitive())
                return -1;
            try {
                return e.getAsJsonPrimitive().getAsInt();
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
    }
}
