package com.modlix.saas.files.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode
@Accessors(chain = true)
public class ImageDetails {
    private Integer width;
    private Integer height;
    private Integer rotation;
    private Integer cropAreaX;
    private Integer cropAreaY;
    private Integer cropAreaWidth;
    private Integer cropAreaHeight;
    private Boolean flipHorizontal = Boolean.FALSE;
    private Boolean flipVertical = Boolean.FALSE;
    private String backgroundColor;
}
