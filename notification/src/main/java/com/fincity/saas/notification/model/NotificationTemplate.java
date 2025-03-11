package com.fincity.saas.notification.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate implements Serializable {

	@Serial
	private static final long serialVersionUID = 5521219668534653174L;

	private String code;
	private Map<String, Map<String, String>> templateParts;
	private Map<String, Object> variableSchema; // NOSONAR
	private Map<String, String> resources;
	private String defaultLanguage;
	private String languageExpression;
	private Map<String, String> recipientExpressions;
	private DeliveryOptions deliveryOptions;
}
