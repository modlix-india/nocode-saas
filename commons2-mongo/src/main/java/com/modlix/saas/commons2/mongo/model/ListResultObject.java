package com.modlix.saas.commons2.mongo.model;

import com.modlix.saas.commons2.model.dto.AbstractOverridableDTO;

public class ListResultObject extends AbstractOverridableDTO<ListResultObject> {

    private static final long serialVersionUID = 4425643888630525907L;

    @Override
    public ListResultObject applyOverride(ListResultObject base) {
        return base;
    }

    @Override
    public ListResultObject makeOverride(ListResultObject base) {
        return base;
    }

}

