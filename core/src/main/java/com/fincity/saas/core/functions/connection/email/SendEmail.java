package com.fincity.saas.core.functions.connection.email;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.json.JsonUtil;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.core.service.connection.email.EmailService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class SendEmail extends AbstractReactiveFunction {

	private static final String EVENT_DATA = "data";

	private static final String FUNCTION_NAME = "SendEmail";

	private static final String NAMESPACE = "CoreServices.Email";

	private static final String APP_CODE = "appCode";

	private static final String CLIENT_CODE = "clientCode";

	private static final String CONNECTION_NAME = "connectionName";

	private static final String TEMPLATE_NAME = "templateName";

	private static final String TEMPLATE_DATA = "templateData";

	private static final String TEMPLATE_DATA_VALUE = "templateDataValue";

	private static final String ADDRESSES = "addresses";

	private static final String ADDRESS = "address";

	private final EmailService emailService;

	public SendEmail(EmailService emailService) {
		this.emailService = emailService;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_DATA, Schema.ofBoolean(EVENT_DATA)));

		Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(

				Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),

				Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),

				Parameter.ofEntry(CONNECTION_NAME, Schema.ofString(CONNECTION_NAME)),

				Parameter.ofEntry(TEMPLATE_NAME, Schema.ofString(TEMPLATE_NAME)),

				Parameter.ofEntry(ADDRESSES, Schema.ofArray(ADDRESSES,
						Schema.ofString(ADDRESS).setFormat(StringFormat.EMAIL)).setDefaultValue(new JsonArray(0))),

				Parameter.ofEntry(TEMPLATE_DATA, Schema.ofObject(TEMPLATE_DATA).setAdditionalProperties(
								new AdditionalType().setSchemaValue(Schema.ofAny(TEMPLATE_DATA_VALUE)))
						.setDefaultValue(new JsonObject()))
		));

		return new FunctionSignature().setNamespace(NAMESPACE).setName(FUNCTION_NAME).setParameters(parameters)
				.setEvents(Map.of(event.getName(), event));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String appCode = context.getArguments().get(APP_CODE).getAsString();
		String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();
		String connectionName = context.getArguments().get(CONNECTION_NAME).getAsString();
		String templateName = context.getArguments().get(TEMPLATE_NAME).getAsString();

		List<String> addressesList = JsonUtil.toList(context.getArguments().get(ADDRESSES).getAsJsonArray())
				.stream().map(Object::toString).toList();

		Map<String, Object> templateData = JsonUtil.toMap(context.getArguments().get(TEMPLATE_DATA).getAsJsonObject());

		return Mono.deferContextual(cv -> {
			if (!"true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))) {
				return Mono.empty();
			}

			Mono<Boolean> emailSent = emailService.sendEmail(appCode, clientCode, addressesList, templateName,
					connectionName, templateData);

			return emailSent.map(e -> new FunctionOutput(List.of(EventResult.outputOf(
					Map.of(EVENT_DATA, new JsonPrimitive(e))))));
		});
	}
}