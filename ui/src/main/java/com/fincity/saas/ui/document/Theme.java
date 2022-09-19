package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'clientCode': 1}", name = "themeFilteringIndex")
@Accessors(chain = true)
public class Theme extends AbstractUIDTO {

	private static final long serialVersionUID = 4355909627072800292L;

	private Map<String, Map<String, Object>> styles; //NOSONAR
	private Map<String, Map<String, String>> variables;
	private Map<String, Map<String, Object>> variableGroups; //NOSONAR
}
