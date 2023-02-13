package com.fincity.saas.commons.util;

public enum FlatFileType {

    CSV("text/csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    TSV("text"),
    XML("application/xml"),
    ;
    
    private String mimeType;
    
    private FlatFileType(String mimeType) {
        
        this.mimeType = mimeType;
    }
    
    public String getMimeType() {
        return this.mimeType;
    }
}
