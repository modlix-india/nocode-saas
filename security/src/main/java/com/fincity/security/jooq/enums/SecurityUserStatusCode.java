/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.enums;


import org.jooq.Catalog;
import org.jooq.EnumType;
import org.jooq.Schema;


/**
 * Status of the user
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public enum SecurityUserStatusCode implements EnumType {

    ACTIVE("ACTIVE"),

    INACTIVE("INACTIVE"),

    DELETED("DELETED"),

    LOCKED("LOCKED"),

    PASSWORD_EXPIRED("PASSWORD_EXPIRED");

    private final String literal;

    private SecurityUserStatusCode(String literal) {
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
    public static SecurityUserStatusCode lookupLiteral(String literal) {
        return EnumType.lookupLiteral(SecurityUserStatusCode.class, literal);
    }
}
