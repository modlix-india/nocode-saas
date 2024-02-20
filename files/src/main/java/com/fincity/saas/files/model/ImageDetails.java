package com.fincity.saas.files.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode
@Accessors(chain = true)
@AllArgsConstructor
public class ImageDetails {
	private Integer width;
	private Integer height;
	private Integer rotation;
	private Integer xAsix;
	private Integer yAxis;
	private Integer cropAreaWidth;
	private Integer cropAreaHeight;
	private Boolean flipHorizontal = Boolean.FALSE;
	private Boolean flipVertical = Boolean.FALSE;
	private String backgroundColor;
	private Boolean keepAspectRatio = Boolean.TRUE;
	private Integer scaleX;
	private Integer scaleY;
	private String name;
}
