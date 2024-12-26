package com.fincity.saas.core.functions.crypto.mac;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;

import io.jsonwebtoken.security.SignatureException;
import reactor.core.publisher.Mono;

public class MacSigner extends AbstractSigner {

	private static final String EVENT_DATA = "result";

	private static final String FUNCTION_NAME = "MacSigner";

	private static final String NAMESPACE = "CoreServices.crypto.mac";

	private static final String APP_CODE = "appCode";

	private static final String CLIENT_CODE = "clientCode";

	private static final String ALGORITHM = "alg";

	private static final String SECRET_KEY = "secretKey";

	private static final String DATA = "data";

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);

		Mac mac = getMacInstance();
		try {
			mac.init(secretKeySpec);
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT)
				.setParameters(Map.of(EVENT_DATA, Schema.ofString(EVENT_DATA)));

		Map<String, Parameter> parameters = Map.ofEntries(

				Parameter.ofEntry(ALGORITHM, Schema.ofString(ALGORITHM)),

				Parameter.ofEntry(SECRET_KEY, Schema.ofString(SECRET_KEY)),

				Parameter.ofEntry(DATA, Schema.ofAnyNotNull(DATA))
		);

		return new FunctionSignature().setName(FUNCTION_NAME).setNamespace(NAMESPACE).setParameters(parameters)
				.setEvents(Map.of(event.getName(), event));
	}

	protected Mac getMacInstance() throws SignatureException {
		try {
			return doGetMacInstance();
		} catch (NoSuchAlgorithmException e) {
			String msg = "Unable to obtain JCA MAC algorithm '" + alg.getJcaName() + "': " + e.getMessage();
			throw new SignatureException(msg, e);
		} catch (InvalidKeyException e) {
			String msg = "The specified signing key is not a valid " + alg.name() + " key: " + e.getMessage();
			throw new SignatureException(msg, e);
		}
	}

	protected Mac doGetMacInstance() throws NoSuchAlgorithmException, InvalidKeyException {
		Mac mac = Mac.getInstance(alg.getJcaName());
		mac.init(key);
		return mac;
	}
}
