package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Map;

import com.fincity.saas.notification.dto.base.BaseInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Template extends BaseInfo<Template> {

	@Serial
	private static final long serialVersionUID = 6999959337815146234L;

	private Map<String, Map<String, String>> templateParts;
	private Map<String, String> resources;
	private Map<String, String> variables;
	private String templateType;
	private String defaultLanguage;
	private String languageExpression;
}
