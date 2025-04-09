package com.fincity.saas.commons.crypto;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.SignatureException;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.security.Key;
import java.security.PrivateKey;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.Getter;

@Getter
public enum SignatureAlgo {
    NONE("none", "No digital signature or MAC performed", "None", "", false, 0, 0),

    HS256("HS256", "HMAC using SHA-256", "HMAC", "HmacSHA256", true, 256, 256),

    HS384("HS384", "HMAC using SHA-384", "HMAC", "HmacSHA384", true, 384, 384),

    HS512("HS512", "HMAC using SHA-512", "HMAC", "HmacSHA512", true, 512, 512),

    RS256("RS256", "RSASSA-PKCS-v1_5 using SHA-256", "RSA", "SHA256withRSA", true, 256, 2048),

    RS384("RS384", "RSASSA-PKCS-v1_5 using SHA-384", "RSA", "SHA384withRSA", true, 384, 2048),

    RS512("RS512", "RSASSA-PKCS-v1_5 using SHA-512", "RSA", "SHA512withRSA", true, 512, 2048),

    ES256("ES256", "ECDSA using P-256 and SHA-256", "ECDSA", "SHA256withECDSA", true, 256, 256),

    ES384("ES384", "ECDSA using P-384 and SHA-384", "ECDSA", "SHA384withECDSA", true, 384, 384),

    ES512("ES512", "ECDSA using P-521 and SHA-512", "ECDSA", "SHA512withECDSA", true, 512, 521),

    PS256("PS256", "RSASSA-PSS using SHA-256 and MGF1 with SHA-256", "RSA", "RSASSA-PSS", false, 256, 2048),

    PS384("PS384", "RSASSA-PSS using SHA-384 and MGF1 with SHA-384", "RSA", "RSASSA-PSS", false, 384, 2048),

    PS512("PS512", "RSASSA-PSS using SHA-512 and MGF1 with SHA-512", "RSA", "RSASSA-PSS", false, 512, 2048);

    // purposefully ordered higher to lower:
    private static final List<SignatureAlgo> PREFERRED_HMAC_ALGS = List.of(HS512, HS384, HS256);
    // purposefully ordered higher to lower:
    private static final List<SignatureAlgo> PREFERRED_EC_ALGS = List.of(ES512, ES384, ES256);
    private static final Map<String, SignatureAlgo> BY_NAME = new HashMap<>();
    private static final Map<String, SignatureAlgo> BY_JCA_NAME = new HashMap<>();

    static {
        for (SignatureAlgo alg : values()) {
            BY_NAME.put(alg.value.toLowerCase(), alg);
            BY_JCA_NAME.put(alg.jcaName.toLowerCase(), alg);
        }
    }

    private final String value;
    private final String description;
    private final String familyName;
    private final String jcaName;
    private final boolean jdkStandard;
    private final int digestLength;
    private final int minKeyLength;

    SignatureAlgo(
            String value,
            String description,
            String familyName,
            String jcaName,
            boolean jdkStandard,
            int digestLength,
            int minKeyLength) {
        this.value = value;
        this.description = description;
        this.familyName = familyName;
        this.jcaName = jcaName;
        this.jdkStandard = jdkStandard;
        this.digestLength = digestLength;
        this.minKeyLength = minKeyLength;
    }

    private static String keyType(boolean signing) {
        return signing ? "signing" : "verification";
    }

    public static SignatureAlgo getByName(String algorithm) {
        return BY_NAME.get(algorithm.toLowerCase());
    }

    public static SignatureAlgo getByJcaName(String jcaName) {
        return BY_JCA_NAME.get(jcaName.toLowerCase());
    }

    public static SignatureAlgo getByKey(Key key) {

        if (key == null) {
            throw new SignatureException("Key argument cannot be null.");
        }

        if (!(key instanceof SecretKey
                || (key instanceof PrivateKey && (key instanceof ECKey || key instanceof RSAKey)))) {
            throw new SignatureException(
                    "The specified key is of type " + key.getClass().getName() + ". This key is not supported.");
        }

        if (key instanceof SecretKey secretKey) {
            int bitLength = (secretKey.getEncoded() != null ? secretKey.getEncoded().length : 0) * Byte.SIZE;

            for (SignatureAlgo alg : PREFERRED_HMAC_ALGS) {
                if (bitLength >= alg.minKeyLength) {
                    return alg;
                }
            }

            throw new SignatureException("The given key is not supported yet.");
        }

        // TODO check other alg based on keys

        return null;
    }

    public static List<JsonElement> getAvailableAlgos(SignatureAlgo... algorithms) {

        List<SignatureAlgo> selected =
                (algorithms == null || algorithms.length == 0) ? List.of(SignatureAlgo.values()) : List.of(algorithms);

        return selected.stream()
                .map(algo -> new JsonPrimitive(algo.getJcaName()))
                .collect(Collectors.toList());
    }

    public boolean isHmac() {
        return familyName.equals("HMAC");
    }

    public boolean isRsa() {
        return familyName.equals("RSA");
    }

    public boolean isEllipticCurve() {
        return familyName.equals("ECDSA");
    }

    public void isValidSigningKey(Key key) {
        this.isValid(key, true);
    }

    public void isValidVerificationKey(Key key) {
        this.isValid(key, false);
    }

    private void isValid(Key key, boolean signing) {
        if (this == NONE) {
            throw new SignatureException("The 'NONE' signature algorithm does not support cryptographic keys.");
        }

        if (isHmac()) {
            this.validateHmacKey(key, signing);
            return;
        }

        this.validateAsymmetricKey(key, signing);
    }

    private void validateHmacKey(Key key, boolean signing) {
        if (!(key instanceof SecretKey secretKey)) {
            throw new SignatureException(
                    StringFormatter.format("$ $ key must be a SecretKey instance.", this.familyName, keyType(signing)));
        }

        byte[] encoded = secretKey.getEncoded();
        if (encoded == null || secretKey.getAlgorithm() == null) {
            throw new SignatureException(StringFormatter.format(
                    "$ $ key's encoded bytes and algorithm cannot be null.", this.familyName, keyType(signing)));
        }

        this.validateKeySize(encoded.length * Byte.SIZE, signing);
    }

    private void validateAsymmetricKey(Key key, boolean signing) {
        if (signing && !(key instanceof PrivateKey)) {
            throw new SignatureException(
                    StringFormatter.format("$ signing key must be a PrivateKey instance.", this.familyName));
        }

        if (isEllipticCurve()) {
            this.validateECKey(key, signing);
        } else {
            this.validateRSAKey(key, signing);
        }
    }

    private void validateECKey(Key key, boolean signing) {
        if (!(key instanceof ECKey ecKey)) {
            throw new SignatureException(
                    StringFormatter.format("$ $ key must be an ECKey instance.", this.familyName, keyType(signing)));
        }

        int size = ecKey.getParams().getOrder().bitLength();
        this.validateKeySize(size, signing);
    }

    private void validateRSAKey(Key key, boolean signing) {
        if (!(key instanceof RSAKey rsaKey)) {
            throw new SignatureException(
                    StringFormatter.format("$ $ key must be an RSAKey instance.", this.familyName, keyType(signing)));
        }

        int size = rsaKey.getModulus().bitLength();
        this.validateKeySize(size, signing);
    }

    private void validateKeySize(int size, boolean signing) {
        if (size < this.minKeyLength) {
            throw new SignatureException(StringFormatter.format(
                    "The $ key's size is $ bits which is not secure enough for the $ algorithm.",
                    keyType(signing),
                    size,
                    name()));
        }
    }
}
