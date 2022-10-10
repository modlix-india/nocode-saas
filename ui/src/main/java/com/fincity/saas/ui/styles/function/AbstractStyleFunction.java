package com.fincity.saas.ui.styles.function;

import org.slf4j.LoggerFactory;

public abstract class AbstractStyleFunction {
	
	protected abstract String internalExecute(String t, String u);

	public String execute(String functionName, String param) {
		try {
			return this.internalExecute(functionName, param);
		} catch (Exception ex) {
			LoggerFactory.getLogger(getClass()).error("Error in evaluating the parameter {}", param, ex);
		}
		return param;
	}	
}
