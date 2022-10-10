package com.fincity.saas.ui.styles.function;

public class StyleFunctionAverage extends StyleFunctionMultiply {

	@Override
	protected float operation(float b, float s) {
		return (b + s) / 2;
	}
}
