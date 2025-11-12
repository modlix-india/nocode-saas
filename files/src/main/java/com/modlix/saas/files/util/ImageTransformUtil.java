package com.modlix.saas.files.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.imgscalr.Scalr;
import org.springframework.http.HttpStatus;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.util.BooleanUtil;
import com.modlix.saas.commons2.util.CommonsUtil;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.commons2.util.Tuples;
import com.modlix.saas.commons2.util.Tuples.Tuple2;
import com.modlix.saas.files.model.ImageDetails;

public class ImageTransformUtil {

    private ImageTransformUtil() {
    }

    public static BufferedImage transformImage(BufferedImage sourceImage, int imageType, ImageDetails details) {

        BufferedImage bi = scaleImage(sourceImage, details.getWidth(), details.getHeight());

        boolean flipHorizontal = BooleanUtil.safeValueOf(details.getFlipHorizontal());
        boolean flipVertical = BooleanUtil.safeValueOf(details.getFlipVertical());

        if (flipHorizontal) {
            bi = Scalr.rotate(bi, Scalr.Rotation.FLIP_HORZ);
        }

        if (flipVertical) {
            bi = Scalr.rotate(bi, Scalr.Rotation.FLIP_VERT);
        }

        if (details.getRotation() != null && details.getRotation() != 0) {
            bi = rotateImage(bi, imageType, details.getRotation(), details.getBackgroundColor());
        }

        if (details.getCropAreaX() != null && details.getCropAreaY() != null && details.getCropAreaWidth() != null
                && details.getCropAreaHeight() != null) {
            bi = cropImage(bi, details.getCropAreaX(), details.getCropAreaY(), details.getCropAreaWidth(),
                    details.getCropAreaHeight(), details.getBackgroundColor(), imageType);
        }

        return bi;
    }

    public static BufferedImage cropImage(BufferedImage sourceImage, int x, int y, int width, int height,
            String bgColor, int imageType) {

        int newX = Math.max(x, 0);
        int newY = Math.max(y, 0);
        int newWidth = x < 0 ? width + x : width;
        int newHeight = y < 0 ? height + y : height;

        if (newX + newWidth > sourceImage.getWidth()) {
            newWidth = sourceImage.getWidth() - newX;
        }

        if (newY + newHeight > sourceImage.getHeight()) {
            newHeight = sourceImage.getHeight() - newY;
        }

        Image croppedImage = sourceImage.getSubimage(newX, newY, newWidth, newHeight);
        BufferedImage bi = new BufferedImage(width, height, imageType);
        Graphics2D g = bi.createGraphics();
        if (imageType != BufferedImage.TYPE_INT_ARGB) {
            g.setColor(StringUtil.safeIsBlank(bgColor) ? Color.BLACK : getColorFromString(bgColor));
            g.fillRect(0, 0, newWidth, newHeight);
        }

        g.drawImage(croppedImage, x < 0 ? -x : 0, y < 0 ? -y : 0, null);
        g.dispose();
        return bi;
    }

    public static BufferedImage rotateImage(BufferedImage sourceImage, int imageType, Integer rotation,
            String bgColor) {

        double radians = Math.toRadians(rotation);
        int w = (Double.valueOf(Math.abs(sourceImage.getWidth() * Math.cos(radians))
                + Math.abs(sourceImage.getHeight() * Math.sin(radians)))).intValue();
        int h = (Double.valueOf(Math.abs(sourceImage.getWidth() * Math.sin(radians))
                + Math.abs(sourceImage.getHeight() * Math.cos(radians)))).intValue();
        BufferedImage resultImage = new BufferedImage(w, h, imageType);
        Graphics2D g = resultImage.createGraphics();

        if (imageType != BufferedImage.TYPE_INT_ARGB) {
            g.setColor(StringUtil.safeIsBlank(bgColor) ? Color.BLACK : getColorFromString(bgColor));
            g.fillRect(0, 0, w, h);
        }

        int x = -1 * (sourceImage.getWidth() - w) / 2;
        int y = -1 * (sourceImage.getHeight() - h) / 2;
        g.rotate(radians, ((double) w / 2), ((double) h / 2));
        g.drawImage(sourceImage, null, x, y);
        return resultImage;
    }

    public static BufferedImage scaleImage(BufferedImage sourceImage, Integer width, Integer height) {

        if ((width == null && height == null) ||
                ((width != null && height != null) && sourceImage.getWidth() == width
                        && sourceImage.getHeight() == height)) {
            return sourceImage;
        }

        return Scalr.resize(sourceImage, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC,
                CommonsUtil.nonNullValue(width, sourceImage.getWidth()),
                CommonsUtil.nonNullValue(height, sourceImage.getHeight()));
    }

    public static Color getColorFromString(String colorString) {
        if (colorString == null || colorString.trim().length() <= 3) {
            return new Color(0, 0, 0, 0);
        }
        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1);
        }

        if (colorString.length() <= 6) {
            int rgbValue = Integer.parseInt(colorString, 16);
            return new Color(rgbValue);
        }

        int rgbValue = Integer.parseInt(colorString.substring(0, 6), 16);
        int alphaValue = Integer.parseInt(colorString.substring(6), 16);

        int r = (rgbValue >> 16) & 0xFF;
        int g = (rgbValue >> 8) & 0xFF;
        int b = rgbValue & 0xFF;

        return new Color(r, g, b, alphaValue);
    }

    public static Tuple2<BufferedImage, Integer> makeSourceImage(File file,
            String fileName) throws IOException {

        ImageInputStream iis = ImageIO.createImageInputStream(file);

        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);

        if (imageReaders.hasNext()) {
            ImageReader reader = imageReaders.next();
            reader.setInput(iis);
            return Tuples.of(reader.read(0),
                    fileName.toLowerCase().endsWith("png") ? BufferedImage.TYPE_INT_ARGB
                            : BufferedImage.TYPE_INT_RGB);
        }

        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to find the reader for the image.");
    }
}
