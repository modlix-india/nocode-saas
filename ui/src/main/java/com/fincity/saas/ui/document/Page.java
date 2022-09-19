package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.saas.ui.model.ComponentDefinition;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "pageFilteringIndex")
@Accessors(chain = true)
public class Page extends AbstractUIDTO {

	private static final long serialVersionUID = 6899134951550453853L;

	
	private String device;
	private Map<String, Map<String, String>> translations;
	private Map<String, Object> properties; // NOSONAR
	private Map<String, FunctionDefinition> eventFunctions;
	private String rootComponent;
	private Map<String, ComponentDefinition> componentDefinition;
	private String message;
}
