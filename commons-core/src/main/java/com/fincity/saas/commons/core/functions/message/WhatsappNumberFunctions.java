package com.fincity.saas.commons.core.functions.message;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.feign.IFeignMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

/**
 * Administering a tenant's WhatsApp numbers from a server function, so ANY app can own a number:
 * link one (owned by {@code core}, so its traffic arrives as the {@code WHATSAPP_INBOUND} event of
 * the app), list them, read the pairing code, unlink one.
 *
 * <p>The same message-service routes entity-processor uses for leadzump's integration page. Linking
 * is administration, so these act only for the caller's OWN client ({@link MessageTenant#resolveOwn}),
 * except listing, which follows SendWhatsapp's rule (a partner may see the numbers it may send from).
 * Like every message function they run only inside a server function definition; who may call that
 * function is the app's decision (its permission), exactly as for SendWhatsapp.
 *
 * <p>Names are chosen so that none is a prefix or substring of another or of SendWhatsapp.
 */
public final class WhatsappNumberFunctions {

    static final String NAMESPACE = "CoreServices.Message";

    static final String APP_CODE = "appCode";
    static final String CLIENT_CODE = "clientCode";
    static final String PHONE_NUMBER = "phoneNumber";
    static final String PRODUCT_ID = "productId";
    static final String SESSION_CODE = "sessionCode";

    /** The owner a number linked through these functions gets: core, i.e. the app's own EventAction. */
    static final String OWNER_SERVICE = "core";

    private WhatsappNumberFunctions() {
    }

    public static List<AbstractReactiveFunction> all(
            IFeignMessageService messageService, IFeignSecurityService securityService, Gson gson) {
        return List.of(
                new LinkWhatsappNumber(messageService, securityService, gson),
                new ListWhatsappNumbers(messageService, securityService, gson),
                new GetWhatsappPairingCode(messageService, securityService, gson),
                new RemoveWhatsappNumber(messageService, securityService, gson),
                new SetDefaultWhatsappNumber(messageService, securityService, gson));
    }

    private static Parameter optionalString(String name) {
        return Parameter.of(name, Schema.ofString(name).setDefaultValue(new JsonPrimitive("")));
    }

    static String string(Map<String, JsonElement> args, String key) {
        JsonElement e = args.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /** What the four share: the signature shape, the definition-only gate and the tenant. */
    abstract static class Base extends AbstractReactiveFunction {

        protected final IFeignMessageService messageService;
        protected final IFeignSecurityService securityService;
        protected final Gson gson;

        Base(IFeignMessageService messageService, IFeignSecurityService securityService, Gson gson) {
            this.messageService = messageService;
            this.securityService = securityService;
            this.gson = gson;
        }

        abstract String name();

        /** Parameters beyond appCode and clientCode. */
        abstract Map<String, Parameter> parameters();

        abstract String outputName();

        abstract Schema outputSchema();

        /** Whether only the caller's own client may be named (administration) or SendWhatsapp's rule. */
        abstract boolean ownClientOnly();

        abstract Mono<JsonElement> act(Map<String, JsonElement> args, String appCode, String clientCode);

        @Override
        public FunctionSignature getSignature() {

            Map<String, Parameter> params = new HashMap<>(this.parameters());
            params.put(APP_CODE, optionalString(APP_CODE));
            params.put(CLIENT_CODE, optionalString(CLIENT_CODE));

            Event event = new Event()
                    .setName(Event.OUTPUT)
                    .setParameters(Map.of(this.outputName(), this.outputSchema()));

            return new FunctionSignature()
                    .setNamespace(NAMESPACE)
                    .setName(this.name())
                    .setParameters(params)
                    .setEvents(Map.of(event.getName(), event));
        }

        @Override
        protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

            Map<String, JsonElement> args = context.getArguments();

            return Mono.deferContextual(cv -> {
                if (!"true".equals(cv.getOrDefault(DefinitionFunction.CONTEXT_KEY, null)))
                    return Mono.error(new GenericException(HttpStatus.FORBIDDEN,
                            NAMESPACE + "." + this.name() + " can only be called from a server function"));

                Mono<Tuple2<String, String>> tenant = this.ownClientOnly()
                        ? MessageTenant.resolveOwn(this.securityService, string(args, APP_CODE), string(args, CLIENT_CODE))
                        : MessageTenant.resolve(this.securityService, string(args, APP_CODE), string(args, CLIENT_CODE));

                return tenant
                        .flatMap(t -> this.act(args, t.getT1(), t.getT2()))
                        .map(out -> new FunctionOutput(List.of(EventResult.outputOf(Map.of(this.outputName(), out)))))
                        .contextWrite(Context.of(LogUtil.METHOD_NAME, this.name() + ".internalExecute"));
            });
        }

        static Mono<String> required(Map<String, JsonElement> args, String key) {
            String value = string(args, key);
            return StringUtil.safeIsBlank(value)
                    ? Mono.error(new GenericException(HttpStatus.BAD_REQUEST, key + " is required"))
                    : Mono.just(value.trim());
        }
    }

    /**
     * Starts linking a number for the app and the caller's client, owned by core. Output {@code
     * session}: the session snapshot (its {@code code} is the sessionCode the other functions take,
     * and its state). Pairing finishes when the customer scans the code from GetWhatsappPairingCode.
     */
    public static final class LinkWhatsappNumber extends Base {

        public LinkWhatsappNumber(IFeignMessageService m, IFeignSecurityService s, Gson g) {
            super(m, s, g);
        }

        @Override
        String name() {
            return "LinkWhatsappNumber";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(
                    PHONE_NUMBER, Parameter.of(PHONE_NUMBER, Schema.ofString(PHONE_NUMBER)),
                    PRODUCT_ID, optionalString(PRODUCT_ID));
        }

        @Override
        String outputName() {
            return "session";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject("session");
        }

        @Override
        boolean ownClientOnly() {
            return true;
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, String appCode, String clientCode) {
            return required(args, PHONE_NUMBER).flatMap(phone -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put(PHONE_NUMBER, phone);
                String productId = string(args, PRODUCT_ID);
                if (!StringUtil.safeIsBlank(productId))
                    body.put(PRODUCT_ID, productId.trim());
                body.put("ownerService", OWNER_SERVICE);
                return this.messageService.createWhatsappSession(appCode, clientCode, body)
                        .map(this.gson::toJsonTree);
            });
        }
    }

    /** Output {@code numbers}: every session (number) of the tenant, with its state. */
    public static final class ListWhatsappNumbers extends Base {

        public ListWhatsappNumbers(IFeignMessageService m, IFeignSecurityService s, Gson g) {
            super(m, s, g);
        }

        @Override
        String name() {
            return "ListWhatsappNumbers";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of();
        }

        @Override
        String outputName() {
            return "numbers";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofArray("numbers", Schema.ofObject("number"));
        }

        @Override
        boolean ownClientOnly() {
            return false;
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, String appCode, String clientCode) {
            return this.messageService.listWhatsappSessions(appCode, clientCode)
                    .defaultIfEmpty(List.of())
                    .map(this.gson::toJsonTree);
        }
    }

    /** Output {@code pairing}: the pairing code (QR payload) of a number that is still pairing. */
    public static final class GetWhatsappPairingCode extends Base {

        public GetWhatsappPairingCode(IFeignMessageService m, IFeignSecurityService s, Gson g) {
            super(m, s, g);
        }

        @Override
        String name() {
            return "GetWhatsappPairingCode";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(SESSION_CODE, Parameter.of(SESSION_CODE, Schema.ofString(SESSION_CODE)));
        }

        @Override
        String outputName() {
            return "pairing";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofObject("pairing");
        }

        @Override
        boolean ownClientOnly() {
            return true;
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, String appCode, String clientCode) {
            return required(args, SESSION_CODE)
                    .flatMap(code -> this.messageService.getWhatsappSessionQr(appCode, clientCode, code))
                    .map(this.gson::toJsonTree);
        }
    }

    /** Unlinks a number. Output {@code removed}: true. */
    public static final class RemoveWhatsappNumber extends Base {

        public RemoveWhatsappNumber(IFeignMessageService m, IFeignSecurityService s, Gson g) {
            super(m, s, g);
        }

        @Override
        String name() {
            return "RemoveWhatsappNumber";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(SESSION_CODE, Parameter.of(SESSION_CODE, Schema.ofString(SESSION_CODE)));
        }

        @Override
        String outputName() {
            return "removed";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean("removed");
        }

        @Override
        boolean ownClientOnly() {
            return true;
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, String appCode, String clientCode) {
            return required(args, SESSION_CODE)
                    .flatMap(code -> this.messageService.unlinkWhatsappSession(appCode, clientCode, code)
                            .thenReturn((JsonElement) new JsonPrimitive(true)));
        }
    }

    /**
     * Makes a number the tenant's default: the number products that name none send from, and the one
     * SendWhatsapp uses for a blank sessionCode. Output {@code marked}: false when the code names
     * nothing placeable. Own client only, like linking and unlinking.
     */
    public static final class SetDefaultWhatsappNumber extends Base {

        public SetDefaultWhatsappNumber(IFeignMessageService m, IFeignSecurityService s, Gson g) {
            super(m, s, g);
        }

        @Override
        String name() {
            return "SetDefaultWhatsappNumber";
        }

        @Override
        Map<String, Parameter> parameters() {
            return Map.of(SESSION_CODE, Parameter.of(SESSION_CODE, Schema.ofString(SESSION_CODE)));
        }

        @Override
        String outputName() {
            return "marked";
        }

        @Override
        Schema outputSchema() {
            return Schema.ofBoolean("marked");
        }

        @Override
        boolean ownClientOnly() {
            return true;
        }

        @Override
        Mono<JsonElement> act(Map<String, JsonElement> args, String appCode, String clientCode) {
            return required(args, SESSION_CODE)
                    .flatMap(code -> this.messageService.markWhatsappSessionDefault(appCode, clientCode, code)
                            .defaultIfEmpty(Boolean.FALSE)
                            .map(marked -> (JsonElement) new JsonPrimitive(Boolean.TRUE.equals(marked))));
        }
    }
}
