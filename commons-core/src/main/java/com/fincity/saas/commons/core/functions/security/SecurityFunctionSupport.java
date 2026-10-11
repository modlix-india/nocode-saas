package com.fincity.saas.commons.core.functions.security;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.ClientRelationUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

/**
 * What the user and org functions in this package share: reading ids from
 * arguments, the definition-only gate and the "is this client related to the
 * caller's" check.
 *
 * Ids arrive as numbers or as strings, because storages keep security ids in
 * STRING columns and a function passes along whatever it read.
 */
final class SecurityFunctionSupport {

    static final String NAME_SPACE = "CoreServices.Security";

    /** The id parameter that means "the current user" when left at its default. */
    static Schema idSchema(String name) {
        return new Schema()
                .setName(name)
                .setType(Type.of(SchemaType.LONG, SchemaType.STRING))
                .setDefaultValue(new JsonPrimitive(0));
    }

    static Schema idArraySchema(String name) {
        return Schema.ofArray(name, new Schema().setName(name + "Item").setType(Type.of(SchemaType.LONG,
                SchemaType.STRING))).setDefaultValue(new JsonArray());
    }

    /**
     * One id, or null for "not given": a missing argument, JSON null, a blank
     * string and 0 all mean the parameter was left alone.
     */
    static BigInteger idOf(String name, JsonElement element) {

        if (element == null || element.isJsonNull())
            return null;

        if (!element.isJsonPrimitive())
            throw badRequest(name + " must be a number or a numeric string");

        JsonPrimitive p = element.getAsJsonPrimitive();
        String text = p.isNumber() ? p.getAsBigDecimal().toPlainString() : p.getAsString().trim();

        if (StringUtil.safeIsBlank(text))
            return null;

        BigInteger id;
        try {
            id = new BigDecimal(text).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw badRequest(name + " is not an id : " + text);
        }

        if (id.signum() < 0)
            throw badRequest(name + " is not an id : " + text);

        return id.signum() == 0 ? null : id;
    }

    /**
     * The ids in an array argument, in order, without repeats and without the
     * blanks a page tends to leave behind. A single value is taken as a one-item
     * list so a step that passes one id still works.
     */
    static List<BigInteger> idsOf(String name, JsonElement element) {

        if (element == null || element.isJsonNull())
            return List.of();

        Set<BigInteger> ids = new LinkedHashSet<>();

        if (!element.isJsonArray()) {
            BigInteger id = idOf(name, element);
            return id == null ? List.of() : List.of(id);
        }

        for (JsonElement each : element.getAsJsonArray()) {
            BigInteger id = idOf(name, each);
            if (id != null)
                ids.add(id);
        }

        return List.copyOf(ids);
    }

    static JsonArray toJsonArray(Collection<BigInteger> ids) {
        JsonArray array = new JsonArray();
        if (ids != null)
            ids.forEach(id -> array.add(new JsonPrimitive(id)));
        return array;
    }

    /**
     * These functions hand out people and org structure, so like GetClient they
     * answer only inside a server-side function definition, never to a direct
     * call of the system function.
     */
    static Mono<ContextAuthentication> definitionContextAuthentication(String functionName) {
        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.getOrDefault(DefinitionFunction.CONTEXT_KEY, null)))
                return Mono.error(new GenericException(HttpStatus.FORBIDDEN,
                        NAME_SPACE + "." + functionName + " can only be called from a server function"));

            return SecurityContextUtil.getUsersContextAuthentication()
                    .switchIfEmpty(Mono.error(() -> new GenericException(HttpStatus.FORBIDDEN,
                            NAME_SPACE + "." + functionName + " needs a security context")));
        });
    }

    /**
     * True when the target client is the caller's own, one the caller's client manages (any depth),
     * or, for the partner case, the caller's client's DIRECT managing client (never SYSTEM) when it has write access
     * to {@code appCode}. Narrower than the storage layer's two-way rule on purpose: these functions
     * hand out names, emails and phone numbers, and every client is managed by SYSTEM.
     * See {@link ClientRelationUtil}.
     *
     * A target that security cannot place (a client id that does not exist answers 500 "No client hierarchy
     * found") is NOT related: the check fails closed instead of failing the whole call, so one unknown id in a
     * list no longer turns GetClientsByIds and friends into a 500.
     */
    static Mono<Boolean> isRelatedClient(IFeignSecurityService securityService, BigInteger callerClientId,
            BigInteger targetClientId, String appCode) {
        return ClientRelationUtil.isRelatedClient(securityService, callerClientId, targetClientId, appCode)
                .onErrorReturn(Boolean.FALSE);
    }

    /** The code-based twin of {@link #isRelatedClient}, for a clientCode argument. */
    static Mono<Boolean> isRelatedClientCode(IFeignSecurityService securityService, String callerClientCode,
            String targetClientCode, String appCode) {
        return ClientRelationUtil.isRelatedClientCode(securityService, callerClientCode, targetClientCode, appCode);
    }

    static GenericException badRequest(String message) {
        return new GenericException(HttpStatus.BAD_REQUEST, message);
    }

    static GenericException forbidden(String message) {
        return new GenericException(HttpStatus.FORBIDDEN, message);
    }

    private SecurityFunctionSupport() {
    }
}
