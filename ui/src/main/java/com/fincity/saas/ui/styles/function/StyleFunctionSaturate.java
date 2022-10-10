package com.fincity.saas.ui.styles.function;

public class StyleFunctionSaturate extends StyleFunctionDarken {

	@Override
	public int componentIndex() {
		return 1;
	}

	@Override
	public float precentFactor(int p) {
		return -p;
	}
}