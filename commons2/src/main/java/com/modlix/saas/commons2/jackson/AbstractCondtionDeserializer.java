package com.modlix.saas.commons2.jackson;

import java.io.IOException;

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
import com.modlix.saas.commons2.util.StringUtil;

public class AbstractCondtionDeserializer extends StdDeserializer<AbstractCondition> {

	private static final long serialVersionUID = 1484554729924047293L;

	public AbstractCondtionDeserializer() {
		super(AbstractCondition.class);
	}

	@Override
	public AbstractCondition deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException {

		TreeNode node = p.readValueAsTree();

		TreeNode operatorNode = node.get("operator");
		if (StringUtil.safeIsBlank(operatorNode) || !(operatorNode instanceof TextNode)) {
			TreeNode fieldNode = node.get("field");
			if (StringUtil.safeIsBlank(fieldNode) || !(fieldNode instanceof TextNode)) {
				throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid condition");
			} else {
				return p.getCodec()
						.treeToValue(node, FilterCondition.class);
			}
		}

		String operator = ((TextNode) operatorNode).asText();

		for (ComplexConditionOperator each : ComplexConditionOperator.values()) {
			if (StringUtil.safeEquals(each.name(), operator)) {
				return p.getCodec()
						.treeToValue(node, ComplexCondition.class);
			}
		}

		return p.getCodec()
				.treeToValue(node, FilterCondition.class);
	}

}
