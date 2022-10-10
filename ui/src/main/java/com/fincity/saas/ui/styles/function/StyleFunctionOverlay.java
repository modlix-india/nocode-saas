package com.fincity.saas.ui.styles.function;

public class StyleFunctionOverlay extends StyleFunctionMultiply {

	@Override
	protected float operation(float s, float b) {
		if (s <= 0.5)
			return b * s;
		else
			return (1 - ((1 - b) * (1 - (2 * s - 1))));
	}
}
