package com.fincity.saas.ui.styles.function;

public class StyleFunctionGreyscale extends StyleFunctionDesaturate {

	@Override
	public String internalExecute(String functionName, String t) {
		return super.internalExecute(functionName, t + ", 100%");
	}
}
