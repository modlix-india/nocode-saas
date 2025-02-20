package com.fincity.saas.files.enums;

public enum ImagesFormatForResize {

    JPG,
    JPEG,
    PNG;

    public static ImagesFormatForResize fromFileName(String fileName) {
        String name = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return ImagesFormatForResize.valueOf(name.toUpperCase());
    }
}
