package com.fincity.saas.ui.styles.function;

public class StyleFunctionLighten extends StyleFunctionDarken {

	@Override
	public float precentFactor(int p) {
		return -p;
	}
}
