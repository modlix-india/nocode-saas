package com.fincity.saas.commons.util;

import com.fincity.saas.commons.enums.StringEncoder;
import com.fincity.saas.commons.exeception.GenericException;
import com.google.gson.JsonElement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class HashUtil {

    private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);

    private static final ConcurrentHashMap<String, MessageDigest> digestCache = new ConcurrentHashMap<>();

    private static final StringEncoder encoder = StringEncoder.HEX;

    private HashUtil() {}

    public static String sha256Hash(Object object) {
        if (object == null) return null;

        try {
            MessageDigest md = getMessageDigest("SHA-256");
            byte[] messageDigest = md.digest(object.toString().getBytes());
            return encoder.encode(messageDigest);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }

    public static <T> JsonElement createHash(T input, String algorithm) {

        if (StringUtil.safeIsBlank(algorithm))
            throw new GenericException(HttpStatus.BAD_REQUEST, "Algorithm cannot be null or empty");

        MessageDigest md = getMessageDigest(algorithm);
        byte[] hash = md.digest(convertToBytes(input));
        return encoder.encodeToJson(hash);
    }

    private static <T> byte[] convertToBytes(T input) {
        if (input == null) return new byte[0];

        if (input instanceof Number number)
            return number instanceof Double || number instanceof Float
                    ? doubleToBytes(number.doubleValue())
                    : longToBytes(number.longValue());

        return input.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFFL);
            value >>= 8;
        }
        return result;
    }

    private static byte[] doubleToBytes(double value) {
        long bits = Double.doubleToLongBits(value);
        return longToBytes(bits);
    }

    private static MessageDigest getMessageDigest(String alg) {
        return digestCache.computeIfAbsent(alg, HashUtil::doGetMessageDigest);
    }

    private static MessageDigest doGetMessageDigest(String alg) {
        try {
            return MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "Hash algorithm not supported: " + alg, e);
        }
    }
}
