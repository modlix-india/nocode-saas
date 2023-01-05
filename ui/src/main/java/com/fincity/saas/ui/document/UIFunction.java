package com.fincity.saas.ui.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.document.AbstractFunction;

@Document(collection = "function")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "uiFunctionFilteringIndex")
public class UIFunction extends AbstractFunction<UIFunction> {

	private static final long serialVersionUID = 6627498650566959804L;
	
	public UIFunction() {
	}
	
	public UIFunction(UIFunction function) {
		super(function);
	}
}
