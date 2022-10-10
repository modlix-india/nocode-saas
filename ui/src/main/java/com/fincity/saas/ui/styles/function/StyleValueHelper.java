package com.fincity.saas.ui.styles.function;

import com.fincity.saas.commons.util.MathUtil;

public class StyleValueHelper {

	public static int[] hsvToRgb(int h, int s, int v) {
		int[] rgb = new int[3];
		int region;
		int remainder;
		int p;
		int q;
		int t;

		if (s == 0) {
			rgb[0] = v;
			rgb[1] = v;
			rgb[2] = v;
			return rgb;
		}

		region = h / 43;
		remainder = (h - (region * 43)) * 6;

		p = (v * (255 - s)) >> 8;
		q = (v * (255 - ((s * remainder) >> 8))) >> 8;
		t = (v * (255 - ((s * (255 - remainder)) >> 8))) >> 8;

		switch (region) {
		case 0:
			rgb[0] = v;
			rgb[1] = t;
			rgb[2] = p;
			break;
		case 1:
			rgb[0] = q;
			rgb[1] = v;
			rgb[2] = p;
			break;
		case 2:
			rgb[0] = p;
			rgb[1] = v;
			rgb[2] = t;
			break;
		case 3:
			rgb[0] = p;
			rgb[1] = q;
			rgb[2] = v;
			break;
		case 4:
			rgb[0] = t;
			rgb[1] = p;
			rgb[2] = v;
			break;
		default:
			rgb[0] = v;
			rgb[1] = p;
			rgb[2] = q;
			break;
		}

		return rgb;
	}

	public static int[] rgbToHsl(int pR, int pG, int pB) {

		float[] hsl = rgbToHslF(pR / 255f, pG / 255f, pB / 255f);
		return new int[] { Math.round(hsl[0] * 360), Math.round(hsl[1] * 100), Math.round(hsl[2] * 100) };
	}

	public static float[] rgbToHslF(float r, float g, float b) {

		float max = MathUtil.max(r, g, b);
		float min = MathUtil.min(r, g, b);

		float h;
		float s;
		float l;
		l = (max + min) / 2.0f;

		if (max == min) {
			
			h = s = 0.0f;
		} else {

			float d = max - min;
			s = (l > 0.5f) ? d / (2.0f - max - min) : d / (max + min);

			if (r > g && r > b)
				h = (g - b) / d + (g < b ? 6.0f : 0.0f);

			else if (g > b)
				h = (b - r) / d + 2.0f;

			else
				h = (r - g) / d + 4.0f;

			h /= 6.0f;
		}

		return new float[] { h, s, l };
	}

	public static int[] hslToRgbF(float h, float s, float l) {
		float r;
		float g;
		float b;

		if (s == 0f) {
			r = g = b = l;
		} else {
			float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
			float p = 2 * l - q;
			r = hueToRgb(p, q, h + 1f / 3f);
			g = hueToRgb(p, q, h);
			b = hueToRgb(p, q, h - 1f / 3f);
		}

		return new int[] { to255(r), to255(g), to255(b) };
	}

	public static int to255(float v) {
		return (int) Math.min(255, Math.floor(256 * v));
	}

	public static float hueToRgb(float p, float q, float t) {
		if (t < 0f)
			t += 1f;
		if (t > 1f)
			t -= 1f;
		if (t < 1f / 6f)
			return p + (q - p) * 6f * t;
		if (t < 1f / 2f)
			return q;
		if (t < 2f / 3f)
			return p + (q - p) * (2f / 3f - t) * 6f;
		return p;
	}

	public static float[] hexToRgbAF(String hex) {
		hex = hex.charAt(0) == '#' ? hex.substring(1) : hex;
		float[] rgb = new float[hex.length() == 3 || hex.length() == 6 ? 3 : 4];
		int charLen = hex.length() > 4 ? 2 : 1;

		for (int i = 0; i < hex.length(); i += charLen) {
			String num = hex.substring(i, i + charLen);
			rgb[i / charLen] = Integer.parseInt(charLen == 1 ? num + num : num, 16) / 256f;
		}

		return rgb;
	}

	public static String rgbToHex(Float... rgbo) {
		return "#" + formatString(2, Integer.toHexString(Math.round(rgbo[0])), '0')
		        + formatString(2, Integer.toHexString(Math.round(rgbo[1])), '0')
		        + formatString(2, Integer.toHexString(Math.round(rgbo[2])), '0')
		        + (rgbo.length > 3 && rgbo[3] != null
		                ? formatString(2, Integer.toHexString(Math.round(rgbo[3] * 256)), '0')
		                : "");
	}

	public static String formatInteger(int width, Integer num, boolean fillZeros) {

		String formatString = "%" + (fillZeros ? "0" : "") + width + "d";

		return String.format(formatString, num);
	}

	public static String formatString(int width, String num, char filler) {

		if (num.length() >= width)
			return num;

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < width - num.length(); i++) {
			sb.append(filler);
		}
		sb.append(num);

		return sb.toString();
	}

	private StyleValueHelper() {
	}
}
