package com.fincity.saas.ui.styles.function;

public class StyleFunctionSoftlight extends StyleFunctionMultiply {

	@Override
	protected float operation(float b, float s) {
		return s <= 0.5 ? 
				b - (1 - 2 * s) * b * (1 - b) :
				b + (2 * s - 1) * ((b <= 0.25 ? 
						((16 * b - 12) * b + 4) * b :
						(float) Math.sqrt(b)) - b);
	}
}
