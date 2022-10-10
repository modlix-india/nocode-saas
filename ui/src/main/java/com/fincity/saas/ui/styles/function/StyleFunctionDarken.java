package com.fincity.saas.ui.styles.function;

public class StyleFunctionDarken extends AbstractStyleFunction implements IComponentPercentBasedFunction {

	@Override
	public String internalExecute(String functionName, String t) {

		String[] x = t.split(",");

		float[] rgb = StyleValueHelper.hexToRgbAF(x[0].trim());
		Float percent = null;
		if (rgb.length == 4)
			percent = rgb[3];
		rgb = StyleValueHelper.rgbToHslF(rgb[0], rgb[1], rgb[2]);
		int p = Integer.parseInt(x[1].trim().replace("\\%", ""));
		rgb[componentIndex()] -= precentFactor(p) / 100f;

		if (rgb[componentIndex()] < 0)
			rgb[componentIndex()] = 0;
		
		if (rgb[componentIndex()] > 1)
			rgb[componentIndex()] = 1;

		final int[] rgbo = StyleValueHelper.hslToRgbF(rgb[0], rgb[1], rgb[2]);

		return StyleValueHelper.rgbToHex((float) rgbo[0], (float) rgbo[1], (float) rgbo[2], percent);
	}

	@Override
	public float precentFactor(int p) {
		return p;
	}
	
	@Override
	public int componentIndex() {
		return 2;
	}
}
