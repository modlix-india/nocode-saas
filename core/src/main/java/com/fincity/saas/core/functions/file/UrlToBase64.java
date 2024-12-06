package com.fincity.saas.core.functions.file;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.core.feign.IFeignFilesService;

import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class UrlToBase64 extends AbstractReactiveFunction {

    private static final String FILE_TYPE = "fileType";

    private static final String METADATA_REQUIRED = "metadataRequired";

    private static final String NAMESPACE = "CoreServices.File";

    private static final String NAME = "UrlToBase64";

    private static final String URL = "url";

    private static final String EVENT_RESULT = "result";

    private final IFeignFilesService filesService;


    Event event = new Event().setName(Event.OUTPUT)
            .setParameters(Map.of(EVENT_RESULT, Schema.ofString(EVENT_RESULT)));

    Event errorEvent = new Event().setName(Event.ERROR)
            .setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));


    public UrlToBase64(IFeignFilesService filesService) {
        this.filesService = filesService;
    }

    @Override
    public FunctionSignature getSignature() {

        return new FunctionSignature().setNamespace(NAMESPACE).setName(NAME).setParameters(Map.of(
                URL, Parameter.of(URL, Schema.ofString(URL).setDefaultValue(new JsonPrimitive(""))),
                FILE_TYPE,
                Parameter.of(FILE_TYPE, Schema.ofString(FILE_TYPE)
                        .setEnums(
                                List.of(new JsonPrimitive("static"), new JsonPrimitive("secured")))
                        .setDefaultValue(new JsonPrimitive("static"))),
                METADATA_REQUIRED,
                Parameter.of(METADATA_REQUIRED,
                        Schema.ofBoolean(METADATA_REQUIRED)
                                .setDefaultValue(new JsonPrimitive(false)))))
                .setEvents(Map.of(Event.OUTPUT, event, Event.ERROR, errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

        String url = context.getArguments().get(URL).getAsString();

        String fileType = context.getArguments().get(FILE_TYPE).getAsString();

        boolean metadataRequired = context.getArguments().get(METADATA_REQUIRED).getAsBoolean();

        if (StringUtil.isNullOrBlank(url))
            return Mono.just(new FunctionOutput(List.of(EventResult.of(errorEvent.getName(),
                    Map.of(Event.ERROR, new JsonPrimitive("Please provide the url."))))));


        return  SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {
                        return this.filesService.convertToBase64(fileType,CommonsUtil.nonNullValue(ca.getClientCode(),
                                                ca.getUrlClientCode()), url, metadataRequired)
                               .map(e -> new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, new JsonPrimitive(e))))))
                               .switchIfEmpty(Mono.just(new FunctionOutput(List.of(EventResult.of(errorEvent.getName(), 
                                    Map.of(EVENT_RESULT, new JsonPrimitive("File cannot be converted to base64")))))));

                        });

    }

}
