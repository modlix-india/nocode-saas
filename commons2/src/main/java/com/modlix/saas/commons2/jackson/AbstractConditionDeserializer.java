package com.modlix.saas.commons2.jackson;

import java.io.IOException;
import java.io.Serial;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.model.condition.HavingCondition;
import com.modlix.saas.commons2.util.StringUtil;

public class AbstractConditionDeserializer extends StdDeserializer<AbstractCondition> {

    @Serial
    private static final long serialVersionUID = 1484554729924047293L;

    public AbstractConditionDeserializer() {
        super(AbstractCondition.class);
    }

    @Override
    public AbstractCondition deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        TreeNode node = p.readValueAsTree();

        if (node == null || node.size() == 0) return null;

        // ComplexCondition: check operator first
        TreeNode operatorNode = node.get("operator");
        if (operatorNode instanceof TextNode) {
            String operator = ((TextNode) operatorNode).asText();

            for (ComplexConditionOperator each : ComplexConditionOperator.values()) {
                if (StringUtil.safeEquals(each.name(), operator))
                    return p.getCodec().treeToValue(node, ComplexCondition.class);
            }
        }

        // HavingCondition: presence of aggregateFunction determines it
        if (node.get("aggregateFunction") instanceof TextNode) {
            return p.getCodec().treeToValue(node, HavingCondition.class);
        }

        // Fallback to FilterCondition validation
        TreeNode fieldNode = node.get("field");
        if (StringUtil.safeIsBlank(fieldNode) || !(fieldNode instanceof TextNode)) {
            if (node.size() == 0) return null;
            throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid condition");
        }

        return p.getCodec().treeToValue(node, FilterCondition.class);
    }
}
