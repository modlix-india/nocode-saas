package com.fincity.saas.ui.styles.function;

public class StyleFunctionRGB extends AbstractStyleFunction {

	@Override
	public String internalExecute(String functionName, String t) {
		
		if (t.indexOf('/') != -1) {
			return new StyleFunctionRGBA().internalExecute(functionName, t);
		}
		
		String[] x = t.split(",");
		int[] rgb = new int[3];

		for (int i = 0; i < 3; i++) {
			final String v = x[i].trim();
			final int p = v.indexOf('%');
			if (p == -1)
				rgb[i] = Integer.parseInt(v);
			else
				rgb[i] = Math.round((Integer.parseInt(v.substring(0, p)) * 256) / 100f);
		}

		return StyleValueHelper.rgbToHex((float) rgb[0], (float) rgb[1], (float) rgb[2]);
	}
}
