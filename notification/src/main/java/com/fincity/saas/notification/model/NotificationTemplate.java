package com.fincity.saas.notification.model;

import java.util.Map;

import com.google.gson.JsonObject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

	private String code;
	private Map<String, Map<String, String>> templateParts;
	private JsonObject variableSchema; // NOSONAR
	private Map<String, String> resources;
	private String defaultLanguage;
	private String languageExpression;

	public boolean isValidSchema() {

		if (this.variableSchema == null)
			return true;

		return !this.variableSchema.isJsonNull() && !this.variableSchema.isJsonObject();
	}
}
