package com.fincity.saas.core.model;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;

@Data
public class DataObject implements Serializable {

	private static final long serialVersionUID = 2698669653996010003L;
	
	private String message;
	private Map<String, Object> data; //NOSONAR
}
