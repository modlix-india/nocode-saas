package com.fincity.saas.commons.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.MessageDigest;

public class HashUtil {

    private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);

    private HashUtil() {
    }

    public static String sha256Hash(Object object) {
        if (object == null) return null;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(object.toString().getBytes());
            return new BigInteger(1, messageDigest).toString(16);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }
}
