package com.fincity.saas.commons.crypto;

import com.fincity.saas.commons.exeception.SignatureException;
import reactor.core.publisher.Mono;

public interface ReactiveSigner {

    Mono<byte[]> sign(byte[] data) throws SignatureException;

    Mono<Boolean> isValid(byte[] data, byte[] signature);
}
