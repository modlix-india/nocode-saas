package com.fincity.saas.core.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.document.AbstractFiller;

@Document(collection = "filler")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "coreFillerFilteringIndex")
public class CoreFiller extends AbstractFiller<CoreFiller> {

	private static final long serialVersionUID = -3965005226382696687L;

	public CoreFiller() {
	}

	public CoreFiller(CoreFiller filler) {
		super(filler);
	}
}
