package com.fincity.saas.core.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.document.AbstractSchema;

@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "coreSchemaFilteringIndex")
public class CoreSchema extends AbstractSchema<CoreSchema> {

	private static final long serialVersionUID = -3965005226382696687L;

	public CoreSchema() {
	}
	
	public CoreSchema(CoreSchema schema) {
		super(schema);
	}
}
