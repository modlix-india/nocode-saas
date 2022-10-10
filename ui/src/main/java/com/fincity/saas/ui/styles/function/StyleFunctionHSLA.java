package com.fincity.saas.ui.styles.function;

public class StyleFunctionHSLA extends AbstractStyleFunction {

	@Override
	public String internalExecute(String functionName, String t) {

		String[] x = t.split(",");
		int[] rgb = new int[3];
		Float percent = null;

		for (int i = 0; i < 4; i++) {
			final String v = x[i].trim();
			if (i == 0)
				rgb[i] = Integer.parseInt(v);
			else if (i == 3) {
				percent = Float.parseFloat(v);
			} else {
				final int p = v.indexOf('%');
				rgb[i] = Math.round(Integer.parseInt(v.substring(0, p)));
			}
		}
		rgb = StyleValueHelper.hslToRgbF(rgb[0] / 360f, rgb[1] / 100f, rgb[2] / 100f);

		return StyleValueHelper.rgbToHex((float) rgb[0], (float) rgb[1], (float) rgb[2], percent);
	}
}
