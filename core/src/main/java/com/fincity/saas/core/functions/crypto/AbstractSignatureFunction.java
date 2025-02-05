package com.fincity.saas.core.functions.crypto;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.saas.commons.crypto.ReactiveSigner;
import com.fincity.saas.commons.crypto.SignatureAlgo;
import com.fincity.saas.commons.enums.StringEncoder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public abstract class AbstractSignatureFunction extends AbstractReactiveFunction {

	protected static final String NAMESPACE = "CoreServices.Crypto";

	protected static final String ALGORITHM = "alg";

	protected static final String CHARSET = "charset";

	protected static final String SIGNATURE_ENCODER = "signatureEncoder";

	protected static final String EVENT_DATA = "result";

	protected static final String IS_VALID = "isValid";

	protected static final String DATA = "data";

	protected Schema getAlgorithms() {
		return Schema.ofString(ALGORITHM).setEnums(
				SignatureAlgo.getAvailableAlgos(
						SignatureAlgo.HS256,
						SignatureAlgo.HS384,
						SignatureAlgo.HS512
				)
		);
	}

	protected Schema getCharsets() {
		return Schema.ofString(CHARSET).setEnums(
				List.of(new JsonPrimitive(StandardCharsets.US_ASCII.name()),
						new JsonPrimitive(StandardCharsets.ISO_8859_1.name()),
						new JsonPrimitive(StandardCharsets.UTF_8.name()),
						new JsonPrimitive(StandardCharsets.UTF_16BE.name()),
						new JsonPrimitive(StandardCharsets.UTF_16LE.name()),
						new JsonPrimitive(StandardCharsets.UTF_16.name())
				)
		).setDefaultValue(new JsonPrimitive(StandardCharsets.UTF_8.name()));
	}

	protected Schema getEncoders() {
		return Schema.ofString(SIGNATURE_ENCODER).setEnums(
				StringEncoder.getAvailableEncoder()
		).setDefaultValue(new JsonPrimitive(StringEncoder.BASE64.getName()));
	}

	protected Mono<FunctionOutput> sign(ReactiveSigner reactiveSigner, JsonElement data, Charset charset, StringEncoder encoder) {

		if (reactiveSigner == null || data == null || data.isJsonNull() || charset == null || encoder == null)
			return Mono.just(new FunctionOutput(
					List.of(EventResult.outputOf(
							Map.of(EVENT_DATA, new JsonPrimitive("")))
					)));

		return reactiveSigner.sign(this.toBytes(data, charset))
				.map(encoder::encode)
				.map(encoded ->
						new FunctionOutput(
								List.of(EventResult.outputOf(
										Map.of(EVENT_DATA, new JsonPrimitive(encoded))
								))));

	}

	protected Mono<FunctionOutput> isValid(ReactiveSigner reactiveSigner, JsonElement data, String signature, Charset charset, StringEncoder encoder) {

		if (reactiveSigner == null || data == null || data.isJsonNull() || signature == null)
			return Mono.just(new FunctionOutput(
					List.of(EventResult.outputOf(
							Map.of(IS_VALID, new JsonPrimitive(Boolean.FALSE)))
					)));

		return reactiveSigner.isValid(this.toBytes(data, charset), encoder.decode(signature))
				.map(isValid ->
						new FunctionOutput(
								List.of(EventResult.outputOf(
										Map.of(IS_VALID, new JsonPrimitive(isValid))
								))));
	}

	protected byte[] toBytes(JsonElement data, Charset charset) {
		return data.isJsonPrimitive() ? this.toBytes(data.getAsString(), charset) : this.toBytes(data.toString(), charset);
	}

	protected byte[] toBytes(String data, Charset charset) {
		return data.getBytes(charset);
	}

	protected String toString(byte[] data, Charset charset) {
		return new String(data, charset);
	}
}
