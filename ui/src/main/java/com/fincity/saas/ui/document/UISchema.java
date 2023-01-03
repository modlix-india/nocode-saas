package com.fincity.saas.ui.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.document.AbstractSchema;

@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "uiSchemaFilteringIndex")
public class UISchema extends AbstractSchema<UISchema> {

	private static final long serialVersionUID = 7384666590217161918L;

	public UISchema() {
	}
	
	public UISchema(UISchema schema) {
		super(schema);
	}
}
