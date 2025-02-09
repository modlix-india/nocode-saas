package com.fincity.saas.files.model;

import com.fincity.saas.commons.util.HashUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode
@Accessors(chain = true)
public class DownloadOptions {

    private Integer width;
    private Integer height;
    private Fit fit;
    private Gravity gravity;
    private String background;
    private Format format;
    private Boolean noCache;
    private Boolean download;
    private String name;

    public enum Gravity {
        auto,
        left,
        right,
        top,
        bottom
    }

    public enum Fit {
        scale_down,
        contain,
        cover,
        crop,
        pad,
    }

    public enum Format {
        general,
        auto,
        avif,
        webp,
        jpeg,
        png
    }

    public String eTagCode() {

        return HashUtil.sha256Hash((width != null ? width.toString() : "-1") +
            (height != null ? height.toString() : "-1") +
            (background == null ? "no" : background) +
            '-' +
            fit +
            '-' +
            gravity + '-' + format);
    }

    public boolean getDownload() {
        return download != null && download;
    }

    public boolean hasNoModification() {
        return height == null && width == null
            && fit == Fit.contain
            && gravity == Gravity.auto
            && background == null
            && format == Format.general;
    }
}
