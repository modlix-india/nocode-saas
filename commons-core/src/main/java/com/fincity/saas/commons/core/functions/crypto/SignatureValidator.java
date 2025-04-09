package com.fincity.saas.commons.core.functions.crypto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.crypto.ReactiveSigner;
import com.fincity.saas.commons.crypto.SignatureAlgo;
import com.fincity.saas.commons.crypto.mac.MacSigner;
import com.fincity.saas.commons.crypto.rsa.RsaSigner;
import com.fincity.saas.commons.enums.StringEncoder;
import com.google.gson.JsonElement;
import java.nio.charset.Charset;
import java.util.Map;
import reactor.core.publisher.Mono;

public class SignatureValidator extends AbstractSignatureFunction {

    private static final String FUNCTION_NAME = "SignatureValidator";

    private static final String SECRET_KEY = "secretKey";

    private static final String SIGNATURE = "signature";

    @Override
    protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
        String algorithm = context.getArguments().get(ALGORITHM).getAsString();
        String charset = context.getArguments().get(CHARSET).getAsString();

        String secretKey = context.getArguments().get(SECRET_KEY).getAsString();
        JsonElement data = context.getArguments().get(DATA);
        String signatureEncoder = context.getArguments().get(SIGNATURE_ENCODER).getAsString();

        Charset cs = Charset.forName(charset);
        StringEncoder encoder = StringEncoder.getByName(signatureEncoder);
        SignatureAlgo algo = SignatureAlgo.getByJcaName(algorithm);

        String signature = context.getArguments().get(SIGNATURE).getAsString();

        ReactiveSigner reactiveSigner = null;

        if (algo.isHmac()) reactiveSigner = new MacSigner(algo, super.toBytes(secretKey, cs), Boolean.TRUE);

        if (algo.isRsa()) reactiveSigner = new RsaSigner(algo, null);

        return super.isValid(reactiveSigner, data, signature, cs, encoder);
    }

    @Override
    public FunctionSignature getSignature() {
        Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_DATA, Schema.ofString(EVENT_DATA)));

        Map<String, Parameter> parameters = Map.ofEntries(
                Parameter.ofEntry(ALGORITHM, super.getAlgorithms()),
                Parameter.ofEntry(CHARSET, super.getCharsets()),
                Parameter.ofEntry(SECRET_KEY, Schema.ofString(SECRET_KEY).setMinimum(0)),
                Parameter.ofEntry(DATA, Schema.ofAnyNotNull(DATA)),
                Parameter.ofEntry(SIGNATURE, Schema.ofString(SIGNATURE).setMinimum(0)),
                Parameter.ofEntry(SIGNATURE_ENCODER, super.getEncoders()));

        return new FunctionSignature()
                .setName(FUNCTION_NAME)
                .setNamespace(NAMESPACE)
                .setParameters(parameters)
                .setEvents(Map.of(event.getName(), event));
    }
}
