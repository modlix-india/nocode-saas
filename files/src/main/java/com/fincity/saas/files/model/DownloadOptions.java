package com.fincity.saas.files.model;

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

	public enum ResizeDirection {
		HORIZONTAL, VERTICAL;
	}

	public String eTagCode() {

		return "" + new StringBuilder(32).append(width == null ? -1 : width)
		        .append(height == null ? -1 : height)
		        .append(bandColor == null ? "no" : bandColor)
		        .append('-')
		        .append(resizeDirection)
		        .append('-')
		        .append(keepAspectRatio.booleanValue() ? 1 : 0)
		        .toString()
		        .hashCode();
	}

	public boolean hasModification() {

		return !(width == null && height == null);
	}
}
