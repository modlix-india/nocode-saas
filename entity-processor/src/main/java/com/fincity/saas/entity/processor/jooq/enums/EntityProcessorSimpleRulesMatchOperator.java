/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.processor.jooq.enums;


import org.jooq.Catalog;
import org.jooq.EnumType;
import org.jooq.Schema;


/**
 * Operator for this Simple Rule.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public enum EntityProcessorSimpleRulesMatchOperator implements EnumType {

    EQUALS("EQUALS"),

    LESS_THAN("LESS_THAN"),

    GREATER_THAN("GREATER_THAN"),

    LESS_THAN_EQUAL("LESS_THAN_EQUAL"),

    GREATER_THAN_EQUAL("GREATER_THAN_EQUAL"),

    IS_TRUE("IS_TRUE"),

    IS_FALSE("IS_FALSE"),

    IS_NULL("IS_NULL"),

    BETWEEN("BETWEEN"),

    IN("IN"),

    LIKE("LIKE"),

    STRING_LOOSE_EQUAL("STRING_LOOSE_EQUAL"),

    MATCH("MATCH"),

    MATCH_ALL("MATCH_ALL"),

    TEXT_SEARCH("TEXT_SEARCH");

    private final String literal;

    private EntityProcessorSimpleRulesMatchOperator(String literal) {
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
    public static EntityProcessorSimpleRulesMatchOperator lookupLiteral(String literal) {
        return EnumType.lookupLiteral(EntityProcessorSimpleRulesMatchOperator.class, literal);
    }
}
