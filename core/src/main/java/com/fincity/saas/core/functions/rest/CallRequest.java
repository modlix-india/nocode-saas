package com.fincity.saas.core.functions.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.dto.RestRequest;
import com.fincity.saas.core.service.connection.rest.RestService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class CallRequest extends AbstractReactiveFunction {

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

	private RestService restService;

	private String name;

	private String methodName;

	private boolean hasPayload;

	public CallRequest(RestService restService, String name, String methodName, boolean hasPayload) {

		this.restService = restService;

		this.name = name;

		this.methodName = methodName;

		this.hasPayload = hasPayload;
	}

	public RestService getRestService() {
		return restService;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_DATA, Schema.ofAny(EVENT_DATA),
				EVENT_HEADERS, Schema.ofAny(EVENT_HEADERS), STATUS_CODE, Schema.ofNumber(STATUS_CODE)));

		Event errorEvent = new Event().setName(Event.ERROR).setParameters(Map.of(EVENT_DATA, Schema.ofAny(EVENT_DATA),
				EVENT_HEADERS, Schema.ofAny(EVENT_HEADERS), STATUS_CODE, Schema.ofNumber(STATUS_CODE)));

		Map<String, Parameter> params = new HashMap<>(Map.ofEntries(

				Parameter.ofEntry(URL, Schema.ofString(URL)),

				Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),

				Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
				Parameter.ofEntry(HEADERS,
						Schema.ofObject(HEADERS).setAdditionalProperties(
								new AdditionalType().setSchemaValue(Schema.ofString("stringValue"))).setDefaultValue(new JsonObject())),

				Parameter.ofEntry(QUERY_PARAMS,
						Schema.ofObject(QUERY_PARAMS).setAdditionalProperties(
								new AdditionalType().setSchemaValue(Schema.ofString("stringValue"))).setDefaultValue(new JsonObject())),

				Parameter.ofEntry(PATH_PARAMS,
						Schema.ofObject(PATH_PARAMS).setAdditionalProperties(
								new AdditionalType().setSchemaValue(Schema.ofString("stringValue"))).setDefaultValue(new JsonObject())),

				Parameter.ofEntry(IGNORE_DEFAULT_HEADERS,
						Schema.ofBoolean(IGNORE_DEFAULT_HEADERS).setDefaultValue(new JsonPrimitive(false))),

				Parameter.ofEntry(TIMEOUT, Schema.ofInteger(TIMEOUT).setDefaultValue(new JsonPrimitive(0))),

				Parameter.ofEntry(CONNECTION_NAME,
						Schema.ofString(CONNECTION_NAME))

		));

		if (this.hasPayload)
			params.put(PAYLOAD, Parameter.of(PAYLOAD, Schema.ofAny(PAYLOAD)));
		if (StringUtil.safeIsBlank(this.methodName)) {
			params.putAll(Map.ofEntries(Parameter.ofEntry(METHOD_NAME,
					Schema.ofString(METHOD_NAME)
							.setEnums(List.of(new JsonPrimitive("GET"), new JsonPrimitive("PUT"),
									new JsonPrimitive("POST"), new JsonPrimitive("PATCH"), new JsonPrimitive("DELETE")))
							.setDefaultValue(new JsonPrimitive("GET")))));
		}

		return new FunctionSignature().setName(this.name).setNamespace(NAME_SPACE).setParameters(params)
				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));

	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String appCode = context.getArguments().get(APP_CODE).getAsString();
		JsonElement payload = context.getArguments().get(PAYLOAD);
		String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
		String url = context.getArguments().get(URL).getAsString();
		String method = StringUtil.safeIsBlank(this.methodName) ? context.getArguments().get(METHOD_NAME).getAsString()
				: this.methodName;
		String connectionName = context.getArguments().get(CONNECTION_NAME).getAsString();
		int timeout = context.getArguments().get(TIMEOUT).getAsInt();
		
		JsonObject headers = context.getArguments().get(HEADERS).getAsJsonObject();
		JsonObject pathParams = context.getArguments().get(PATH_PARAMS).getAsJsonObject();
		JsonObject queryParams = context.getArguments().get(QUERY_PARAMS).getAsJsonObject();
		
		
		
		boolean ignoreConnectionHeaders = context.getArguments().get(IGNORE_DEFAULT_HEADERS).getAsJsonPrimitive()
				.getAsBoolean();
		MultiValueMap<String, String> headerMap = new LinkedMultiValueMap<>();
		for(var x : headers.entrySet()) {
			headerMap.add(x.getKey(), x.getValue().getAsString());
		}
		Map<String, String> pathParamsMap = new HashMap<>();
		for(var x : pathParams.entrySet()) {
			pathParamsMap.put(x.getKey(), x.getValue().getAsString());
		}
		Map<String, String> queryParamsMap = new HashMap<>();
		for(var x : queryParams.entrySet()) {
			queryParamsMap.put(x.getKey(), x.getValue().getAsString());
		}
		

		RestRequest request = new RestRequest().setHeaders(headerMap.size() > 0 ? headerMap : null)
				.setIgnoreDefaultHeaders(ignoreConnectionHeaders).setMethod(method).setPathParameters(pathParamsMap)
				.setQueryParameters(queryParamsMap.size() > 0 ? queryParamsMap : null).setTimeout(timeout)
				.setPayload(payload).setUrl(url);

		Gson gson = new Gson();
		return restService.doCall(appCode, clientCode, connectionName, request)
				.map(obj -> new FunctionOutput(
						List.of(EventResult.outputOf(Map.of(EVENT_DATA, gson.toJsonTree(obj.getData()), EVENT_HEADERS,
								gson.toJsonTree(obj.getHeaders()), STATUS_CODE, gson.toJsonTree(obj.getStatus()))))));

	}
}
