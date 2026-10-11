package com.fincity.saas.commons.core.functions.message;

import java.util.HashMap;
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
import com.fincity.nocode.reactor.util.FlatMapUtil;
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

/**
 * Sends a WhatsApp message through one of the tenant's linked numbers.
 *
 * <p>The same message-service route entity-processor sends through, so pacing, the typing indicator
 * and the bridge's own refusals all apply unchanged. What entity-processor checked before calling it,
 * deal access, the opt-out and its pacing holds, is the calling function's job now.
 *
 * <p>{@code sessionCode} blank sends from the tenant's default number. Slow by design: the bridge
 * holds the call for its paced gap, so a step can take most of a minute.
 */
public class SendWhatsapp extends AbstractReactiveFunction {

    private static final String FUNCTION_NAME = "SendWhatsapp";
    private static final String NAMESPACE = "CoreServices.Message";

    static final String APP_CODE = "appCode";
    static final String CLIENT_CODE = "clientCode";
    static final String SESSION_CODE = "sessionCode";
    static final String TO = "to";
    static final String TEXT = "text";
    static final String FILE_PATH = "filePath";
    static final String KIND = "kind";
    static final String MIME_TYPE = "mimeType";
    static final String FILE_NAME = "fileName";
    static final String VOICE_NOTE = "voiceNote";
    static final String RESOURCE_TYPE = "resourceType";

    static final String EVENT_RESULT = "result";
    static final String EVENT_SESSION_CODE = "sessionCode";

    private final IFeignMessageService messageService;
    private final IFeignSecurityService securityService;
    private final Gson gson;

    public SendWhatsapp(IFeignMessageService messageService, IFeignSecurityService securityService, Gson gson) {
        this.messageService = messageService;
        this.securityService = securityService;
        this.gson = gson;
    }

    private static Parameter optionalString(String name) {
        return Parameter.of(name, Schema.ofString(name).setDefaultValue(new JsonPrimitive("")));
    }

    @Override
    public FunctionSignature getSignature() {

        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(
                        EVENT_RESULT, Schema.ofObject(EVENT_RESULT),
                        EVENT_SESSION_CODE, Schema.ofString(EVENT_SESSION_CODE)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Map.entry(APP_CODE, optionalString(APP_CODE)),
                Map.entry(CLIENT_CODE, optionalString(CLIENT_CODE)),
                Map.entry(SESSION_CODE, optionalString(SESSION_CODE)),
                Map.entry(TO, Parameter.of(TO, Schema.ofString(TO))),
                Map.entry(TEXT, optionalString(TEXT)),
                Map.entry(FILE_PATH, optionalString(FILE_PATH)),
                Map.entry(KIND, optionalString(KIND)),
                Map.entry(MIME_TYPE, optionalString(MIME_TYPE)),
                Map.entry(FILE_NAME, optionalString(FILE_NAME)),
                Map.entry(VOICE_NOTE, Parameter.of(VOICE_NOTE,
                        Schema.ofBoolean(VOICE_NOTE).setDefaultValue(new JsonPrimitive(false)))),
                Map.entry(RESOURCE_TYPE, optionalString(RESOURCE_TYPE))));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(parameters)
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        Map<String, JsonElement> args = context.getArguments();

        String to = string(args, TO);
        String text = string(args, TEXT);
        String filePath = string(args, FILE_PATH);

        return Mono.deferContextual(cv -> {
            if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))) return Mono.empty();

            if (StringUtil.safeIsBlank(to))
                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "to is required"));

            if (StringUtil.safeIsBlank(text) && StringUtil.safeIsBlank(filePath))
                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "text or filePath is required"));

            return FlatMapUtil.flatMapMono(
                            () -> MessageTenant.resolve(
                                    this.securityService, string(args, APP_CODE), string(args, CLIENT_CODE)),
                            tenant -> this.sessionCode(tenant.getT1(), tenant.getT2(), string(args, SESSION_CODE)),
                            (tenant, session) -> this.messageService.sendWhatsappSessionMessage(
                                    tenant.getT1(), tenant.getT2(), session, request(args)),
                            (tenant, session, sent) -> Mono.just(new FunctionOutput(List.of(EventResult.outputOf(Map.of(
                                    EVENT_RESULT, this.gson.toJsonTree(sent),
                                    EVENT_SESSION_CODE, new JsonPrimitive(session)))))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "SendWhatsapp.internalExecute"));
        });
    }

    /** The code asked for if it is placeable, else the tenant's default, as the message service decides. */
    private Mono<String> sessionCode(String appCode, String clientCode, String sessionCode) {
        return this.messageService
                .resolveWhatsappSession(appCode, clientCode, StringUtil.safeIsBlank(sessionCode) ? null : sessionCode)
                .mapNotNull(session -> session.get("code") == null ? null : session.get("code").toString())
                .switchIfEmpty(Mono.error(new GenericException(
                        HttpStatus.CONFLICT, "No WhatsApp number is linked for " + clientCode + " in " + appCode)));
    }

    /** The body the session route takes, with blanks left out so it decides between text and media. */
    static Map<String, Object> request(Map<String, JsonElement> args) {

        Map<String, Object> request = new HashMap<>();
        request.put(TO, string(args, TO));

        for (String key : List.of(TEXT, FILE_PATH, KIND, MIME_TYPE, FILE_NAME, RESOURCE_TYPE)) {
            String value = string(args, key);
            if (!StringUtil.safeIsBlank(value)) request.put(key, value);
        }

        JsonElement voiceNote = args.get(VOICE_NOTE);
        if (voiceNote != null && voiceNote.isJsonPrimitive() && voiceNote.getAsBoolean()) request.put(VOICE_NOTE, true);

        return request;
    }

    static String string(Map<String, JsonElement> args, String key) {
        JsonElement e = args.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
