package com.fincity.saas.ui.styles.function;

public class StyleFunctionHardlight extends StyleFunctionMultiply {

	@Override
	protected float operation(float b, float s) {
		if (s <= 0.5)
			return b * (2 * s);
		else
			return (1 - ((1 - b) * (1 - (2 * s - 1))));
	}
}
