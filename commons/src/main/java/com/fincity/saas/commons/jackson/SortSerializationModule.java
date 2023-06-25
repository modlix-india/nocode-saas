package com.fincity.saas.commons.jackson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.openfeign.support.SortJsonComponent;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.NullHandling;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fincity.saas.commons.util.BooleanUtil;

public class SortSerializationModule extends Module {

	@Override
	public String getModuleName() {
		return "SortModule";
	}

	@Override
	public Version version() {
		return new Version(0, 1, 0, "", null, null);
	}

	@Override
	public void setupModule(SetupContext context) {
		SimpleSerializers serializers = new SimpleSerializers();
		serializers.addSerializer(Sort.class, new SortJsonComponent.SortSerializer());
		context.addSerializers(serializers);

		SimpleDeserializers deserializers = new SimpleDeserializers();
		deserializers.addDeserializer(Sort.class, new SortDeserializer());
		context.addDeserializers(deserializers);
	}

	public static class SortDeserializer extends JsonDeserializer<Sort> {

		@Override
		public Sort deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
		        throws IOException {
			TreeNode treeNode = jsonParser.getCodec()
			        .readTree(jsonParser);
			if (treeNode.isArray()) {
				ArrayNode arrayNode = (ArrayNode) treeNode;
				List<Sort.Order> orders = new ArrayList<>();
				for (JsonNode jsonNode : arrayNode) {

					Sort.Direction direction = jsonNode.has("direction")
					        ? Sort.Direction.valueOf(jsonNode.get("direction")
					                .textValue())
					        : Sort.DEFAULT_DIRECTION;

					NullHandling nullHandling = jsonNode.has("nullHandling")
					        ? NullHandling.valueOf(jsonNode.get("nullHandling")
					                .textValue())
					        : null;

					String property = jsonNode.get("property")
					        .textValue();

					Sort.Order order = nullHandling == null ? new Sort.Order(direction, property)
					        : new Sort.Order(direction, property, nullHandling);

					if (jsonNode.has("ignoreCase") && BooleanUtil.safeValueOf(jsonNode.get("ignoreCase")
					        .textValue()))
						order = order.ignoreCase();

					orders.add(order);
				}
				return Sort.by(orders);
			}
			return null;
		}

		@Override
		public Class<Sort> handledType() {
			return Sort.class;
		}

	}
}
