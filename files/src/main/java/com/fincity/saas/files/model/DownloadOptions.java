package com.fincity.saas.files.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode
@Accessors(chain = true)
public class DownloadOptions {

	private String path;
	private Integer width;
	private Integer height;
	private Boolean keepAspectRatio = Boolean.TRUE;
}
