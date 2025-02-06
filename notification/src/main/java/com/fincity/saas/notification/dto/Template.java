package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Template extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 2999999794759806228L;

	private ULong clientId;
	private ULong appId;
	private String code;
	private String name;
	private String description;
	private Map<String, Map<String, String>> templateParts;
	private Map<String, String> resources;
	private Map<String, String> variables;
	private String templateType;
	private String defaultLanguage;
	private String language;
}
