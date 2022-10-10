package com.fincity.saas.ui.styles.function;

public class StyleFunctionNegation extends StyleFunctionMultiply {

	@Override
	protected float operation(float b, float s) {
		float c = b - s;
		
		return c < 0 ? 1 + c : c;
	}
}
