package com.fincity.saas.ui.styles.function;

public class StyleFunctionScreen extends StyleFunctionMultiply {

	@Override
	protected float operation(float b, float s) {
		return (1 - ((1 - b) * (1 - s)));
	}
}
