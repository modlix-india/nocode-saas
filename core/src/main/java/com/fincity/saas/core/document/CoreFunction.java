package com.fincity.saas.core.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.document.AbstractFunction;

@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "coreFunctionFilteringIndex")
public class CoreFunction extends AbstractFunction<CoreFunction> {

	private static final long serialVersionUID = 229558978137767412L;
	
	public CoreFunction() {
	}
	
	public CoreFunction(CoreFunction function) {
		super(function);
	}
}
