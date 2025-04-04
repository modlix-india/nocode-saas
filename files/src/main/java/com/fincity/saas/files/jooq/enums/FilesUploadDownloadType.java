/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.files.jooq.enums;


import org.jooq.Catalog;
import org.jooq.EnumType;
import org.jooq.Schema;


/**
 * Type of the ZIP
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public enum FilesUploadDownloadType implements EnumType {

    UPLOAD("UPLOAD"),

    DOWNLOAD("DOWNLOAD");

    private final String literal;

    private FilesUploadDownloadType(String literal) {
        this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
        return null;
    }

    @Override
    public Schema getSchema() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    /**
     * Lookup a value of this EnumType by its literal. Returns
     * <code>null</code>, if no such value could be found, see {@link
     * EnumType#lookupLiteral(Class, String)}.
     */
    public static FilesUploadDownloadType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(FilesUploadDownloadType.class, literal);
    }
}
