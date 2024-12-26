package com.fincity.saas.core.functions.crypto.mac;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;

import io.jsonwebtoken.security.SignatureException;

public abstract class AbstractSigner extends AbstractReactiveFunction {

	protected abstract byte[] sign(byte[] data) throws SignatureException;
}
