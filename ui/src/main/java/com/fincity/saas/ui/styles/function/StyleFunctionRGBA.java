package com.fincity.saas.ui.styles.function;

public class StyleFunctionRGBA extends AbstractStyleFunction {

	@Override
	public String internalExecute(String functionName, String t) {

		int[] rgb = new int[3];
		Float percent = null;

		String[] x = t.split("[, ]{1,}");
		x = new String[] { x[0], x[1], x[2], x.length > 4 ? x[4] : x[3] };

		for (int i = 0; i < 4; i++) {
			final String v = x[i].trim();

			if (i == 3) {
				final int p = v.indexOf('%');
				percent = Float.parseFloat(p == -1 ? v : v.substring(0, p));
				if (percent > 1f)
					percent = percent / 100f;
			} else {
				final int p = v.indexOf('%');
				if (p == -1)
					rgb[i] = Integer.parseInt(v);
				else
					rgb[i] = Math.round((Integer.parseInt(v.substring(0, p)) * 256) / 100f);
			}
		}

		return StyleValueHelper.rgbToHex((float) rgb[0], (float) rgb[1], (float) rgb[2], percent);
	}
}
