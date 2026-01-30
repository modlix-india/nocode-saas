package com.fincity.saas.commons.jackson;

import java.io.IOException;
import java.io.Serial;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.GroupCondition;
import com.fincity.saas.commons.model.condition.HavingCondition;
import com.fincity.saas.commons.util.StringUtil;

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

        if (node.get("havingConditions") instanceof ArrayNode) {
            return p.getCodec().treeToValue(node, GroupCondition.class);
        }

        if (node.get("operator") instanceof TextNode textNode) {
            String operator = textNode.asText();

            for (ComplexConditionOperator each : ComplexConditionOperator.values()) {
                if (StringUtil.safeEquals(each.name(), operator))
                    return p.getCodec().treeToValue(node, ComplexCondition.class);
            }
        }

        if (node.get("aggregateFunction") instanceof TextNode) {
            return p.getCodec().treeToValue(node, HavingCondition.class);
        }

        TreeNode fieldNode = node.get("field");
        if (StringUtil.safeIsBlank(fieldNode) || !(fieldNode instanceof TextNode)) {
            if (node.size() == 0) return null;
            throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid condition");
        }

        return p.getCodec().treeToValue(node, FilterCondition.class);
    }
}
