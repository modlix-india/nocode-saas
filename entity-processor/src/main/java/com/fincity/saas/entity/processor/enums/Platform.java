package com.fincity.saas.entity.processor.enums;

import java.util.TreeMap;
import org.jooq.EnumType;

public enum Platform implements EnumType {
    PRE_QUALIFICATION("PRE_QUALIFICATION", 0),
    POST_QUALIFICATION("POST_QUALIFICATION", 1);

    private static final TreeMap<Integer, Platform> ORDER_MAP = new TreeMap<>();

    static {
        for (Platform platform : values()) {
            ORDER_MAP.put(platform.order, platform);
        }
    }

    private final String literal;
    private final Integer order;

    Platform(String literal, Integer order) {
        this.literal = literal;
        this.order = order;
    }

    public static Platform lookupLiteral(String literal) {
        return EnumType.lookupLiteral(Platform.class, literal);
    }

    public static Platform getFirstPlatform() {
        return ORDER_MAP.firstEntry().getValue();
    }

    public static Platform getLastPlatform() {
        return ORDER_MAP.lastEntry().getValue();
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return null;
    }
}
