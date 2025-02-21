package com.fincity.saas.files.util;

import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.files.model.ImageDetails;

public class ImageDetailsUtil {

    private ImageDetailsUtil() {
    }


    public static ImageDetails makeDetails(String width, String height, String rotation,
                                           String cropAreaX, String cropAreaY,
                                           String cropAreaWidth, String cropAreaHeight,
                                           String flipHorizontal, String flipVertical, String backgroundColor) {
        ImageDetails imageDetails = new ImageDetails()
            .setFlipHorizontal(BooleanUtil.safeValueOf(flipHorizontal))
            .setFlipVertical(BooleanUtil.safeValueOf(flipVertical))
            .setBackgroundColor(backgroundColor);

        if (width != null)
            imageDetails.setWidth(Integer.valueOf(width));

        if (height != null)
            imageDetails.setHeight(Integer.valueOf(height));

        if (rotation != null)
            imageDetails.setRotation(Integer.valueOf(rotation));

        if (cropAreaX != null)
            imageDetails.setCropAreaX(Integer.valueOf(cropAreaX));

        if (cropAreaY != null)
            imageDetails.setCropAreaY(Integer.valueOf(cropAreaY));

        if (cropAreaWidth != null)
            imageDetails.setCropAreaWidth(Integer.valueOf(cropAreaWidth));

        if (cropAreaHeight != null)
            imageDetails.setCropAreaHeight(Integer.valueOf(cropAreaHeight));

        return imageDetails;
    }
}
