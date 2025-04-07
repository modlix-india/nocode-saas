package com.fincity.saas.commons.core.functions.file;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.feign.IFeignFilesService;
import com.fincity.saas.commons.core.service.file.TemplateConversionService;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class TemplateToPdf extends AbstractReactiveFunction {
    private static final String EVENT_DATA = "fileData";

    private static final String FUNCTION_NAME = "TemplateToPdf";

    private static final String NAMESPACE = "CoreServices.File";

    private static final String APP_CODE = "appCode";

    private static final String CLIENT_CODE = "clientCode";

    private static final String TEMPLATE_NAME = "templateName";

    private static final String TEMPLATE_DATA = "templateData";

    private static final String FILE_NAME = "fileName";

    private static final String FILE_LOCATION = "fileLocation";

    private static final String FILE_TYPE = "fileType";

    private static final String FILE_OVERRIDE = "fileOverride";

    private static final String OUTPUT_FORMAT = MediaType.APPLICATION_PDF.getSubtype();

    private final TemplateConversionService templateConversionService;

    private final IFeignFilesService fileService;

    private final Gson gson;

    public TemplateToPdf(
            TemplateConversionService templateConversionService, IFeignFilesService fileService, Gson gson) {
        this.templateConversionService = templateConversionService;
        this.fileService = fileService;
        this.gson = gson;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_DATA, Schema.ofObject(EVENT_DATA)));

        Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(TEMPLATE_NAME, Schema.ofString(TEMPLATE_NAME)),
                Parameter.ofEntry(TEMPLATE_DATA, Schema.ofObject(TEMPLATE_DATA).setDefaultValue(new JsonObject())),
                Parameter.ofEntry(FILE_NAME, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(FILE_LOCATION, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive("/"))),
                Parameter.ofEntry(
                        FILE_TYPE,
                        Schema.ofString(APP_CODE)
                                .setEnums(List.of(new JsonPrimitive("static"), new JsonPrimitive("secured")))
                                .setDefaultValue(new JsonPrimitive("secured"))),
                Parameter.ofEntry(
                        FILE_OVERRIDE, Schema.ofBoolean(FILE_OVERRIDE).setDefaultValue(new JsonPrimitive(false)))));

        return new FunctionSignature()
                .setNamespace(NAMESPACE)
                .setName(FUNCTION_NAME)
                .setParameters(parameters)
                .setEvents(Map.of(event.getName(), event));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        String appCode = context.getArguments().get(APP_CODE).getAsString();
        String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
        String templateName = context.getArguments().get(TEMPLATE_NAME).getAsString();
        Map<String, Object> templateData =
                JsonUtil.toMap(context.getArguments().get(TEMPLATE_DATA).getAsJsonObject());
        String fileName = context.getArguments().get(FILE_NAME).getAsString();
        String fileLocation = context.getArguments().get(FILE_LOCATION).getAsString();
        String fileType = context.getArguments().get(FILE_TYPE).getAsString();
        boolean fileOverride = context.getArguments().get(FILE_OVERRIDE).getAsBoolean();

        return Mono.deferContextual(cv -> {
                    if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))) {
                        return Mono.just(new FunctionOutput(
                                List.of(EventResult.outputOf(Map.of(EVENT_DATA, new JsonPrimitive(false))))));
                    }

                    return FlatMapUtil.flatMapMono(
                            () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                            acTup -> templateConversionService.convert(
                                    templateName, acTup.getT1(), acTup.getT2(), OUTPUT_FORMAT, templateData),
                            (acTup, fileBytes) -> {
                                if (fileBytes == null) return Mono.error(new Exception("Data is not a file"));
                                return makeFileInFiles(
                                        acTup.getT2(),
                                        templateName,
                                        fileName,
                                        fileLocation,
                                        fileType,
                                        fileOverride,
                                        fileBytes);
                            },
                            (acTup, fileBytes, fileDetails) -> processOutput(fileDetails));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TemplateToPdf.internalExecute"));
    }

    private Mono<FunctionOutput> processOutput(Map<String, Object> fileDetails) {
        if (fileDetails == null) {
            return Mono.just(new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_DATA, new JsonObject())))));
        }

        return Mono.just(
                new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_DATA, gson.toJsonTree(fileDetails))))));
    }

    private Mono<Map<String, Object>> makeFileInFiles(
            String clientCode,
            String templateName,
            String fileName,
            String fileLocation,
            String fileType,
            boolean override,
            byte[] fileBytes) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);

            String finalFileName = fileName;
            if (StringUtil.safeIsBlank(finalFileName)) {
                finalFileName = extractFileName(fileLocation, templateName, clientCode);
            }

            if (StringUtil.safeIsBlank(finalFileName)) {
                finalFileName = "file";
            }

            return this.fileService.create(fileType, clientCode, override, fileLocation, finalFileName, buffer);
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private String extractFileName(String fileLocation, String templateName, String clientCode) {
        if (StringUtil.safeIsBlank(fileLocation)) {
            return templateName + "_" + clientCode;
        }

        String fileName = Path.of(fileLocation).getFileName().toString();
        return fileName.isEmpty() ? templateName + "_" + clientCode : fileName;
    }
}
