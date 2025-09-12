package com.modlix.saas.commons2.crypto;

import com.modlix.saas.commons2.exception.SignatureException;

public interface ImperativeSigner {

    byte[] sign(byte[] data) throws SignatureException;

    boolean isValid(byte[] data, byte[] signature);
}
