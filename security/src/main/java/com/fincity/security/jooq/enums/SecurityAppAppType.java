/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.enums;


import org.jooq.Catalog;
import org.jooq.EnumType;
import org.jooq.Schema;


/**
 * Application type
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public enum SecurityAppAppType implements EnumType {

    APP("APP"),

    SITE("SITE"),

    POSTER("POSTER");

    private final String literal;

    private SecurityAppAppType(String literal) {
        this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
        return null;
    }

    @Override
    public Schema getSchema() {
        return null;
    }

    @Override
    public String getName() {
        return "security_app_APP_TYPE";
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    /**
     * Lookup a value of this EnumType by its literal
     */
    public static SecurityAppAppType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(SecurityAppAppType.class, literal);
    }
}