/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.enums;


import org.jooq.Catalog;
import org.jooq.EnumType;
import org.jooq.Schema;


/**
 * Platform
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public enum SecurityAppRegIntegrationPlatform implements EnumType {

    GOOGLE("GOOGLE"),

    META("META"),

    APPLE("APPLE"),

    SSO("SSO"),

    MICROSOFT("MICROSOFT"),

    X("X");

    private final String literal;

    private SecurityAppRegIntegrationPlatform(String literal) {
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
        return null;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    /**
     * Lookup a value of this EnumType by its literal. Returns
     * <code>null</code>, if no such value could be found, see {@link
     * EnumType#lookupLiteral(Class, String)}.
     */
    public static SecurityAppRegIntegrationPlatform lookupLiteral(String literal) {
        return EnumType.lookupLiteral(SecurityAppRegIntegrationPlatform.class, literal);
    }
}
