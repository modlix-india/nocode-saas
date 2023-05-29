package com.fincity.saas.core.model;

import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProcessedEmailDetails implements Serializable {

	private static final long serialVersionUID = -2941346419092741584L;
	
	private List<String> to;
	private String from;
	private String subject;
	private String body;
}
