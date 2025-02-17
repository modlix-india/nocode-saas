package com.fincity.saas.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fincity.nocode.kirun.engine.json.schema.Schema;

import lombok.Data;

@Data
public class NotificationTemplate implements Serializable {

	@Serial
	private static final long serialVersionUID = 2178854708270744216L;

	private Map<String, Map<String, String>> templateParts;
	private Map<String, Schema> variables;
	private Map<String, String> resources;
	private String defaultLanguage;
	private String languageExpression;
}
