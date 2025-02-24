package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Map;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.notification.dto.base.BaseInfo;
import com.fincity.saas.notification.enums.NotificationChannelType;

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

	private NotificationChannelType channelType;
	private Map<String, Map<String, String>> templateParts;
	private Map<String, Schema> variables;
	private Map<String, String> resources;
	private String defaultLanguage;
	private String languageExpression;
}
