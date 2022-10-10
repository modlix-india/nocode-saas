package com.fincity.saas.ui.styles.function;

public class StyleFunctionExclusion extends StyleFunctionMultiply {

	@Override
	protected float operation(float b, float s) {
		return b + s - 2 * b * s;
	}
}
