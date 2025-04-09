package com.fincity.saas.commons.util;

public enum DataFileType {
    CSV("text/csv"),
    XLS("application/x-excel"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    TSV("text/tab-separated-values"),
    JSON("application/json", true),
    JSONL("application/x-jsonlines", true),
    ;

    private String mimeType;
    private boolean isNestedStructure;

    private DataFileType(String mimeType) {
        this(mimeType, false);
    }

    private DataFileType(String mimeType, boolean isNestedStructure) {
        this.mimeType = mimeType;
        this.isNestedStructure = isNestedStructure;
    }

    public boolean isNestedStructure() {
        return this.isNestedStructure;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public static DataFileType getFileTypeFromExtension(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
        return DataFileType.valueOf(extension.toUpperCase());
    }
}
