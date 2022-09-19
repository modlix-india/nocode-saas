package com.fincity.saas.ui.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "filterFilteringIndex")
@Accessors(chain = true)
public class Function extends AbstractUIDTO {

	private static final long serialVersionUID = 2733397732360134939L;
	
	private FunctionDefinition definition;
}
