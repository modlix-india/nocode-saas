package com.fincity.saas.commons.jackson;

import java.io.IOException;
import java.io.Serial;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.util.StringUtil;

public class AbstractConditionDeserializer extends StdDeserializer<AbstractCondition> {

	@Serial
	private static final long serialVersionUID = 1484554729924047293L;

	public AbstractConditionDeserializer() {
		super(AbstractCondition.class);
	}

	@Override
	public AbstractCondition deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException {

		TreeNode node = p.readValueAsTree();

		// Handle empty JSON object {} - return null instead of throwing error
		if (node == null || node.size() == 0) {
			return null;
		}

		TreeNode operatorNode = node.get("operator");
		if (StringUtil.safeIsBlank(operatorNode) || !(operatorNode instanceof TextNode)) {
			TreeNode fieldNode = node.get("field");
			if (StringUtil.safeIsBlank(fieldNode) || !(fieldNode instanceof TextNode)) {
				// If node is empty or has no recognizable structure, return null
				if (node.size() == 0) {
					return null;
				}
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
