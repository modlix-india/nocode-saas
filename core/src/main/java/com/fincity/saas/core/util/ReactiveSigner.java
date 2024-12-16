package com.fincity.saas.core.util;

import io.jsonwebtoken.security.SignatureException;
import reactor.core.publisher.Mono;

public interface ReactiveSigner {

	Mono<byte[]> sign(byte[] data) throws SignatureException;

	Mono<Boolean> isValid(byte[] data, byte[] signature);
}
