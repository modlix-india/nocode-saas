package com.fincity.saas.files.model;

import com.fincity.saas.commons.util.BooleanUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode
@Accessors(chain = true)
public class DownloadOptions {

	private Integer width;
	private Integer height;
	private Boolean keepAspectRatio = Boolean.TRUE;
	private String bandColor;
	private ResizeDirection resizeDirection = ResizeDirection.HORIZONTAL;
	private Boolean noCache;
	private Boolean download;
	private String name;

	public enum ResizeDirection {
		HORIZONTAL, VERTICAL;
	}

	public String eTagCode() {
		return "" + new StringBuilder(32)
				.append(width != null ? width.toString() : "-1")
				.append(height != null ? height.toString() : "-1")
				.append(bandColor == null ? "no" : bandColor)
				.append('-')
				.append(resizeDirection)
				.append('-')
				.append(BooleanUtil.safeValueOf(keepAspectRatio) ? 1 : 0)
				.toString()
				.hashCode();
	}

	public boolean hasModification() {

		return !(width == null && height == null);
	}
}
