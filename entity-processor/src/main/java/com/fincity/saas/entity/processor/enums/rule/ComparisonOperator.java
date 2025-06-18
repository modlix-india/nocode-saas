package com.fincity.saas.entity.processor.enums.rule;

import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ComparisonOperator implements EnumType {
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

    ComparisonOperator(String literal) {
        this.literal = literal;
    }

    public static ComparisonOperator lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ComparisonOperator.class, literal);
    }

    public static ComparisonOperator lookup(FilterConditionOperator operator) {
        return lookupLiteral(operator.name());
    }

    public FilterConditionOperator getConditionOperator() {
        return FilterConditionOperator.valueOf(this.name());
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return null;
    }

    public static void main(String[] args) {}
}
