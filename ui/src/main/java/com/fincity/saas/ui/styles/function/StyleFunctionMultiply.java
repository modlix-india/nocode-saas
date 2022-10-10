package com.fincity.saas.ui.styles.function;

public class StyleFunctionMultiply extends AbstractStyleFunction {

	@Override
	public String internalExecute(String functionName, String t) {
		String[] x = t.split(",");

		float[] rgb1 = StyleValueHelper.hexToRgbAF(x[0].trim());
		float[] rgb2 = StyleValueHelper.hexToRgbAF(x[1].trim());

		float rgb[] = { operation(rgb1[0], rgb2[0]), operation(rgb1[1], rgb2[1]), operation(rgb1[2], rgb2[2]) };
		Float percent = rgb1.length > 3 ? rgb1[3] : null;

		if (rgb2.length > 3) {
			percent = (percent + rgb2[3]) / 2;
		}

		return StyleValueHelper.rgbToHex(rgb[0] * 256, rgb[1] * 256, rgb[2] * 256, percent);
	}

	protected float operation(float b, float s) {
		return b * s;
	}
}
