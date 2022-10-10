package com.fincity.saas.ui.styles.function;

public class StyleFunctionDifference extends StyleFunctionMultiply {

	@Override
	protected float operation(float b, float s) {
		return Math.abs(b - s);
	}
}
