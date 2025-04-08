package com.fincity.saas.commons.core.functions.rest;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.dto.RestRequest;
import com.fincity.saas.commons.core.dto.RestResponse;
import com.fincity.saas.commons.core.feign.IFeignFilesService;
import com.fincity.saas.commons.core.service.connection.rest.RestService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import feign.FeignException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class CallRequest extends AbstractReactiveFunction {
    private static final String STRING_VALUE = "stringValue";

    private static final String URL = "url";

    private static final String NAME_SPACE = "CoreServices.REST";

    private static final String PATH_PARAMS = "pathParams";

    private static final String CLIENT_CODE = "clientCode";

    private static final String EVENT_DATA = "data";

    private static final String EVENT_HEADERS = "headers";

    private static final String HEADERS = "headers";

    private static final String QUERY_PARAMS = "queryParams";

    private static final String STATUS_CODE = "statusCode";

    private static final String TIMEOUT = "timeout";

    private static final String CONNECTION_NAME = "connectionName";

    private static final String APP_CODE = "appCode";

    private static final String PAYLOAD = "payload";

    private static final String METHOD_NAME = "methodName";

    private static final String IGNORE_DEFAULT_HEADERS = "ignoreConnectionHeaders";

    private static final String DOWNLOAD_AS_A_FILE = "downloadAsAFile";

    private static final String FILE_NAME = "fileName";

    private static final String FILE_LOCATION = "fileLocation";

    private static final String FILE_CLIENT_CODE = "fileClientCode";

    private static final String FILE_TYPE = "fileType";

    private static final String FILE_OVERRIDE = "fileOverride";

    private static final String CD_FILE_NAME = "filename";

    private static final String FILE = "file";

    private static final Logger logger = LoggerFactory.getLogger(CallRequest.class);
    private final Gson gson;
    private final IFeignFilesService fileService;
    private final IFeignSecurityService securityService;
    private final RestService restService;
    private final String name;
    private final String methodName;
    private final boolean hasPayload;

    public CallRequest(
            RestService restService,
            IFeignFilesService fileService,
            IFeignSecurityService securityService,
            String name,
            String methodName,
            boolean hasPayload,
            Gson gson) {
        this.restService = restService;
        this.name = name;
        this.methodName = methodName;
        this.hasPayload = hasPayload;
        this.gson = gson;
        this.fileService = fileService;
        this.securityService = securityService;
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event()
                .setName(Event.OUTPUT)
                .setParameters(Map.of(
                        EVENT_DATA,
                        Schema.ofAny(EVENT_DATA),
                        EVENT_HEADERS,
                        Schema.ofAny(EVENT_HEADERS),
                        STATUS_CODE,
                        Schema.ofNumber(STATUS_CODE)));

        Event errorEvent = new Event()
                .setName(Event.ERROR)
                .setParameters(Map.of(
                        EVENT_DATA,
                        Schema.ofAny(EVENT_DATA),
                        EVENT_HEADERS,
                        Schema.ofAny(EVENT_HEADERS),
                        STATUS_CODE,
                        Schema.ofNumber(STATUS_CODE)));

        Map<String, Parameter> params = new HashMap<>(Map.ofEntries(
                Parameter.ofEntry(CONNECTION_NAME, Schema.ofString(CONNECTION_NAME)),
                Parameter.ofEntry(URL, Schema.ofString(URL)),
                Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(
                        HEADERS,
                        Schema.ofObject(HEADERS)
                                .setAdditionalProperties(
                                        new AdditionalType().setSchemaValue(Schema.ofString(STRING_VALUE)))
                                .setDefaultValue(new JsonObject())),
                Parameter.ofEntry(
                        QUERY_PARAMS,
                        Schema.ofObject(QUERY_PARAMS)
                                .setAdditionalProperties(
                                        new AdditionalType().setSchemaValue(Schema.ofString(STRING_VALUE)))
                                .setDefaultValue(new JsonObject())),
                Parameter.ofEntry(
                        PATH_PARAMS,
                        Schema.ofObject(PATH_PARAMS)
                                .setAdditionalProperties(
                                        new AdditionalType().setSchemaValue(Schema.ofString(STRING_VALUE)))
                                .setDefaultValue(new JsonObject())),
                Parameter.ofEntry(
                        IGNORE_DEFAULT_HEADERS,
                        Schema.ofBoolean(IGNORE_DEFAULT_HEADERS).setDefaultValue(new JsonPrimitive(false))),
                Parameter.ofEntry(TIMEOUT, Schema.ofInteger(TIMEOUT).setDefaultValue(new JsonPrimitive(0))),
                Parameter.ofEntry(
                        DOWNLOAD_AS_A_FILE,
                        Schema.ofBoolean(DOWNLOAD_AS_A_FILE).setDefaultValue(new JsonPrimitive(false))),
                Parameter.ofEntry(FILE_NAME, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(FILE_LOCATION, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive("/"))),
                Parameter.ofEntry(FILE_CLIENT_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
                Parameter.ofEntry(
                        FILE_TYPE,
                        Schema.ofString(APP_CODE)
                                .setEnums(List.of(new JsonPrimitive("static"), new JsonPrimitive("secured")))
                                .setDefaultValue(new JsonPrimitive("static"))),
                Parameter.ofEntry(
                        FILE_OVERRIDE, Schema.ofBoolean(FILE_OVERRIDE).setDefaultValue(new JsonPrimitive(false)))));

        if (this.hasPayload) params.put(PAYLOAD, Parameter.of(PAYLOAD, Schema.ofAny(PAYLOAD)));
        if (StringUtil.safeIsBlank(this.methodName)) {
            params.putAll(Map.ofEntries(Parameter.ofEntry(
                    METHOD_NAME,
                    Schema.ofString(METHOD_NAME)
                            .setEnums(List.of(
                                    new JsonPrimitive("GET"),
                                    new JsonPrimitive("PUT"),
                                    new JsonPrimitive("POST"),
                                    new JsonPrimitive("PATCH"),
                                    new JsonPrimitive("DELETE")))
                            .setDefaultValue(new JsonPrimitive("GET")))));
        }

        return new FunctionSignature()
                .setName(this.name)
                .setNamespace(NAME_SPACE)
                .setParameters(params)
                .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
    }

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        String appCode = context.getArguments().get(APP_CODE).getAsString();
        JsonElement payload = context.getArguments().get(PAYLOAD);
        String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
        String url = context.getArguments().get(URL).getAsString();
        String method = StringUtil.safeIsBlank(this.methodName)
                ? context.getArguments().get(METHOD_NAME).getAsString()
                : this.methodName;
        String connectionName = context.getArguments().get(CONNECTION_NAME).getAsString();
        int timeout = context.getArguments().get(TIMEOUT).getAsInt();

        boolean downloadAsAFile = context.getArguments().get(DOWNLOAD_AS_A_FILE).getAsBoolean();

        JsonObject headers = context.getArguments().get(HEADERS).getAsJsonObject();
        JsonObject pathParams = context.getArguments().get(PATH_PARAMS).getAsJsonObject();
        JsonObject queryParams = context.getArguments().get(QUERY_PARAMS).getAsJsonObject();

        boolean ignoreConnectionHeaders = context.getArguments()
                .get(IGNORE_DEFAULT_HEADERS)
                .getAsJsonPrimitive()
                .getAsBoolean();
        MultiValueMap<String, String> headerMap = new LinkedMultiValueMap<>();
        for (var x : headers.entrySet()) {
            headerMap.add(x.getKey(), x.getValue().getAsString());
        }
        Map<String, String> pathParamsMap = new HashMap<>();
        for (var x : pathParams.entrySet()) {
            pathParamsMap.put(x.getKey(), x.getValue().getAsString());
        }
        Map<String, String> queryParamsMap = new HashMap<>();
        for (var x : queryParams.entrySet()) {
            queryParamsMap.put(x.getKey(), x.getValue().getAsString());
        }

        RestRequest request = new RestRequest()
                .setHeaders(!headerMap.isEmpty() ? headerMap : null)
                .setIgnoreDefaultHeaders(ignoreConnectionHeaders)
                .setMethod(method)
                .setPathParameters(pathParamsMap)
                .setQueryParameters(!queryParamsMap.isEmpty() ? queryParamsMap : null)
                .setTimeout(timeout)
                .setPayload(payload)
                .setUrl(url);

        return FlatMapUtil.flatMapMono(
                        () -> restService.doCall(appCode, clientCode, connectionName, request, downloadAsAFile),
                        obj -> {
                            if (obj.getStatus() >= 400 && obj.getStatus() <= 600)
                                return this.makeErrorResponseFunctionOutput(obj);

                            if (downloadAsAFile) {
                                return this.processDownload(
                                        obj,
                                        url,
                                        context.getArguments().get(FILE_NAME).getAsString(),
                                        context.getArguments()
                                                .get(FILE_LOCATION)
                                                .getAsString(),
                                        context.getArguments()
                                                .get(FILE_OVERRIDE)
                                                .getAsBoolean(),
                                        context.getArguments()
                                                .get(FILE_CLIENT_CODE)
                                                .getAsString(),
                                        context.getArguments().get(FILE_TYPE).getAsString());
                            }

                            return this.processOutput(obj);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallRequest.internalExecute"))
                .onErrorResume(this::makeExceptionResponseFunctionOutput);
    }

    private Mono<FunctionOutput> processDownload(
            RestResponse obj,
            String url,
            String fileName,
            String fileLocation,
            boolean override,
            String fileClientCode,
            String fileType) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> {
                            if (!ca.isAuthenticated()) return Mono.empty();

                            if (StringUtil.safeIsBlank(fileClientCode)) return Mono.just(ca.getClientCode());

                            if (ca.getClientCode().equals(fileClientCode)) return Mono.just(ca.getClientCode());

                            return this.securityService
                                    .isBeingManaged(ca.getUrlClientCode(), fileClientCode)
                                    .map(e -> e ? fileClientCode : "");
                        },
                        (ca, cc) -> {
                            if (StringUtil.safeIsBlank(cc)) return Mono.error(new Exception("Client code is invalid"));

                            if (!(obj.getData() instanceof byte[]))
                                return Mono.error(new Exception("Data is not a file"));

                            return this.makeFileInFiles(obj, url, fileName, fileLocation, override, cc, fileType);
                        },
                        (ca, cc, fileObj) -> this.processOutput(obj.setData(fileObj)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CallRequest.processDownload"));
    }

    private Mono<Map<String, Object>> makeFileInFiles(
            RestResponse obj,
            String url,
            String fileName,
            String fileLocation,
            boolean override,
            String cc,
            String fileType) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap((byte[]) obj.getData());

            return this.fileService.create(
                    fileType, cc, override, fileLocation, this.resolveFileName(obj, url, fileName), buffer);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private String resolveFileName(RestResponse obj, String url, String fileName) {
        boolean fileNameEmpty = StringUtil.safeIsBlank(fileName);

        String fileExtension = "";
        String finalFileName = fileNameEmpty ? FILE + UniqueUtil.shortUUID() : fileName;
        String fileNameWithoutExtension = fileNameEmpty
                ? finalFileName
                : finalFileName.contains(".")
                        ? finalFileName.substring(0, finalFileName.lastIndexOf("."))
                        : finalFileName;

        String fileTypeFromContent = obj.getHeaders().get("Content-Type");
        if (!StringUtil.safeIsBlank(fileTypeFromContent)) {
            fileExtension = MediaType.valueOf(fileTypeFromContent).getSubtype();
            finalFileName = fileNameWithoutExtension + "." + fileExtension;
            return finalFileName;
        }

        int urlIndex = url.indexOf(".");
        if (urlIndex != -1) {
            fileExtension = url.substring(urlIndex + 1, !url.contains("?") ? url.length() : url.indexOf("?"));
            finalFileName = fileNameWithoutExtension + "." + fileExtension;
            return finalFileName;
        }

        String contentDisposition = obj.getHeaders().get("Content-Disposition") == null
                ? obj.getHeaders().get("content-disposition")
                : obj.getHeaders().get("Content-Disposition");

        if (!StringUtil.safeIsBlank(contentDisposition)) {
            String cdFileName = this.parseContentDispositionForFileName(contentDisposition);
            if (!StringUtil.safeIsBlank(cdFileName) && cdFileName.contains(".")) {
                int lastDotIndex = cdFileName.lastIndexOf(".");
                fileExtension = cdFileName.substring(lastDotIndex + 1);
                finalFileName = fileNameEmpty ? cdFileName : fileNameWithoutExtension + "." + fileExtension;
                return finalFileName;
            }
        }

        return finalFileName;
    }

    public String parseContentDispositionForFileName(String cd) {
        int index = cd.indexOf(CD_FILE_NAME);

        if (index == -1) return null;

        index = index + CD_FILE_NAME.length();

        if (cd.charAt(index) == '=') {
            return extractFilenameWithoutCharset(cd, index);
        } else if (cd.charAt(index) == '*') {
            return extractFilenameWithCharset(cd, index);
        }

        return null;
    }

    private String extractFilenameWithCharset(String cd, int index) {
        index = index + 2;
        int doub = cd.indexOf('\'', index);
        String charset = cd.substring(index, doub);
        Charset cs = StandardCharsets.UTF_8;
        try {
            cs = Charset.forName(charset);
        } catch (Exception e) {
            logger.error("Charset not found: {}", charset, e);
        }
        index = doub + 1;
        int end = cd.indexOf('\'', index);
        if (end == -1 || end >= cd.length()) return null;
        return URLDecoder.decode(cd.substring(end + 1), cs);
    }

    private String extractFilenameWithoutCharset(String cd, int index) {
        index++;
        if (cd.charAt(index) == '"') {
            index++;
            int end = cd.indexOf('"', index);
            return cd.substring(index, end == -1 ? cd.length() : end);
        } else {
            int end = cd.indexOf(';', index);
            return cd.substring(index, end == -1 ? cd.length() : end);
        }
    }

    private Mono<FunctionOutput> processOutput(RestResponse obj) {
        return Mono.just(new FunctionOutput(List.of(EventResult.outputOf(Map.of(
                EVENT_DATA,
                gson.toJsonTree(obj.getData()),
                EVENT_HEADERS,
                gson.toJsonTree(obj.getHeaders()),
                STATUS_CODE,
                gson.toJsonTree(obj.getStatus()))))));
    }

    private Mono<FunctionOutput> makeErrorResponseFunctionOutput(RestResponse obj) {
        return Mono.just(new FunctionOutput(List.of(
                EventResult.of(
                        Event.ERROR,
                        Map.of(
                                EVENT_DATA,
                                gson.toJsonTree(obj.getData()),
                                EVENT_HEADERS,
                                gson.toJsonTree(obj.getHeaders()),
                                STATUS_CODE,
                                gson.toJsonTree(obj.getStatus()))),
                EventResult.outputOf(Map.of(
                        EVENT_DATA,
                        gson.toJsonTree(Map.of()),
                        EVENT_HEADERS,
                        gson.toJsonTree(Map.of()),
                        STATUS_CODE,
                        gson.toJsonTree(Map.of()))))));
    }

    private Mono<FunctionOutput> makeExceptionResponseFunctionOutput(Throwable ex) {
        if (ex instanceof FeignException feignException) {
            JsonElement je = this.processFeignException(feignException);
            if (je != null) {
                return Mono.just(new FunctionOutput(List.of(
                        EventResult.of(
                                Event.ERROR,
                                Map.of(
                                        EVENT_DATA,
                                        je,
                                        EVENT_HEADERS,
                                        gson.toJsonTree(Map.of()),
                                        STATUS_CODE,
                                        gson.toJsonTree(Map.of()))),
                        EventResult.outputOf(Map.of(
                                EVENT_DATA,
                                gson.toJsonTree(Map.of()),
                                EVENT_HEADERS,
                                gson.toJsonTree(Map.of()),
                                STATUS_CODE,
                                gson.toJsonTree(Map.of()))))));
            }
        }

        return Mono.just(new FunctionOutput(List.of(
                EventResult.of(
                        Event.ERROR,
                        Map.of(
                                EVENT_DATA,
                                gson.toJsonTree(ex.getMessage()),
                                EVENT_HEADERS,
                                gson.toJsonTree(Map.of()),
                                STATUS_CODE,
                                gson.toJsonTree(Map.of()))),
                EventResult.outputOf(Map.of(
                        EVENT_DATA,
                        gson.toJsonTree(Map.of()),
                        EVENT_HEADERS,
                        gson.toJsonTree(Map.of()),
                        STATUS_CODE,
                        gson.toJsonTree(Map.of()))))));
    }

    private JsonElement processFeignException(FeignException fe) {
        Optional<ByteBuffer> op = fe.responseBody();
        ByteBuffer byteBuffer = op.orElse(null);

        if (byteBuffer == null || !byteBuffer.hasArray()) return null;

        Collection<String> contentType = fe.responseHeaders().get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.contains("application/json")) return null;

        try {
            return this.gson.fromJson(new String(byteBuffer.array()), JsonElement.class);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
