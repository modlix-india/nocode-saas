package com.fincity.saas.commons.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;

public class AbstractCondtionDeserializer extends StdDeserializer<AbstractCondition> {

	private static final long serialVersionUID = 1484554729924047293L;

	public AbstractCondtionDeserializer() {
		super(AbstractCondition.class);
	}

	@Override
	public AbstractCondition deserialize(JsonParser p, DeserializationContext ctxt)
	        throws IOException {

		TreeNode node = p.readValueAsTree();

		if (node.get("field") != null)
			return p.getCodec()
			        .treeToValue(node, FilterCondition.class);

		return p.getCodec()
		        .treeToValue(node, ComplexCondition.class);
	}

}
