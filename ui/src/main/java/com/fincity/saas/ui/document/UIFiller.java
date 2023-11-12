package com.fincity.saas.ui.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.document.AbstractFiller;

@Document(collection = "filler")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "uiFillerFilteringIndex")
public class UIFiller extends AbstractFiller<UIFiller> {

	private static final long serialVersionUID = -3965005226382696687L;

	public UIFiller() {
	}

	public UIFiller(UIFiller filler) {
		super(filler);
	}
}
