package com.fincity.saas.ui.model;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;

@Data
public class ComponentDefinition implements Serializable {

	private static final long serialVersionUID = -8719079119317757579L;

	private String key;
	private String name;
	private String type;
	private Map<String, Object> properties; // NOSONAR
	private boolean override;
	private Map<String, Boolean> children;
}
