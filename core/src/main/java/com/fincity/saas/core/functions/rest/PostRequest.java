package com.fincity.saas.core.functions.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.core.service.connection.rest.RestService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class PostRequest extends AbstractReactiveFunction {

	private static final String URL = "url";

	private static final String FUNCTION_NAME = "PostRequest";

	private static final String NAME_SPACE = "CoreServices.REST";

	private static final String PATH_PARAMS = "pathParams";

	private static final String CLIENT_CODE = "clientCode";

	private static final String EVENT_DATA = "data";

	private static final String EVENT_HEADERS = "headers";

	private static final String HEADER_KEYS = "headerKeys";

	private static final String QUERY_PARAM_KEYS = "queryParamKeys";

	private static final String HEADER_VALUES = "headerValues";

	private static final String QUERY_PARAM_VALUES = "queryParamValues";

	private static final String STATUS_CODE = "statusCode";

	private static final String TIMEOUT = "timeout";

	private static final String CONNECTION_NAME = "connectionName";

	private static final String APP_CODE = "appCode";

	private static final String PAYLOAD = "payload";

	private RestService restService;

	public PostRequest(RestService restService) {
		this.restService = restService;
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

		return new FunctionSignature().setName(FUNCTION_NAME).setNamespace(NAME_SPACE)
				.setParameters(Map.ofEntries(Parameter.ofEntry(PAYLOAD, Schema.ofAny(PAYLOAD)),
						Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
						Parameter.ofEntry(CLIENT_CODE,
								Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),
						Parameter.ofEntry(URL, Schema.ofString(URL)),
						Parameter.ofEntry(CONNECTION_NAME,
								Schema.ofString(CONNECTION_NAME).setDefaultValue(new JsonPrimitive(""))),
						Parameter.ofEntry(HEADER_KEYS,
								Schema.ofString(HEADER_KEYS).setDefaultValue(new JsonPrimitive("")), true),
						Parameter.ofEntry(HEADER_VALUES,
								Schema.ofString(HEADER_VALUES).setDefaultValue(new JsonPrimitive("")), true),
						Parameter.ofEntry(PATH_PARAMS,
								Schema.ofString(PATH_PARAMS).setDefaultValue(new JsonPrimitive("")), true),
						Parameter.ofEntry(QUERY_PARAM_KEYS,
								Schema.ofString(QUERY_PARAM_KEYS).setDefaultValue(new JsonPrimitive("")), true),
						Parameter.ofEntry(QUERY_PARAM_VALUES,
								Schema.ofString(QUERY_PARAM_VALUES).setDefaultValue(new JsonPrimitive("")), true),
						Parameter.ofEntry(TIMEOUT, Schema.ofInteger(TIMEOUT).setDefaultValue(new JsonPrimitive(0)))))
				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));

	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String appCode = context.getArguments().get(APP_CODE).getAsString();
		JsonElement payload = context.getArguments().get(PAYLOAD);
		String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
		String url = context.getArguments().get(URL).getAsString();
		String method = "POST";
		String connectionName = context.getArguments().get(CONNECTION_NAME).getAsString();
		int timeout = context.getArguments().get(TIMEOUT).getAsInt();
		JsonArray headerKeys = context.getArguments().get(HEADER_KEYS).getAsJsonArray();
		JsonArray headerValues = context.getArguments().get(HEADER_VALUES).getAsJsonArray();
		JsonArray pathParams = context.getArguments().get(PATH_PARAMS).getAsJsonArray();
		JsonArray queryKeys = context.getArguments().get(QUERY_PARAM_KEYS).getAsJsonArray();
		JsonArray queryValues = context.getArguments().get(QUERY_PARAM_VALUES).getAsJsonArray();
		MultiValueMap<String, String> headerMap = new LinkedMultiValueMap<>();
		Map<String, String> queryParamsMap = new HashMap<>();
		String[] pathParamsArray = new String[pathParams.size()];
		if (headerKeys.size() > 0)
			for (var i = 0; i < headerKeys.size(); i++) {
				if (headerKeys.get(i).getAsString().isBlank())
					continue;
				headerMap.add(headerKeys.get(i).getAsString(), headerValues.get(i).getAsString());
			}

		if (queryKeys.size() > 0)
			for (var i = 0; i < queryKeys.size(); i++) {
				if (queryKeys.get(i).getAsString().isBlank())
					continue;
				queryParamsMap.put(queryKeys.get(i).getAsString(), queryValues.get(i).getAsString());
			}

		if (pathParams.size() > 0)
			for (var i = 0; i < pathParams.size(); i++) {
				if (pathParams.get(i).getAsString().isBlank())
					continue;
				pathParamsArray[i] = pathParams.get(i).getAsString();
			}

		Gson gson = new Gson();
		return restService
				.doCall(appCode, clientCode, connectionName, url, headerMap.size() > 0 ? headerMap : null,
						pathParamsArray, queryParamsMap.size() > 0 ? queryParamsMap : null, timeout, method, payload)
				.map(obj -> new FunctionOutput(
						List.of(EventResult.outputOf(Map.of(EVENT_DATA, gson.toJsonTree(obj.getData()), EVENT_HEADERS,
								gson.toJsonTree(obj.getHeaders()), STATUS_CODE, gson.toJsonTree(obj.getStatus()))))));

	}

}
