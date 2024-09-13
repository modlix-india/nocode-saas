package com.fincity.saas.commons.util.data;

import org.checkerframework.checker.units.qual.s;

import com.fincity.saas.commons.util.UniqueUtil;

public class hel {

    public static void main(String[] args) {
        System.out.println(UniqueUtil.base36UUID().length());
        System.out.println(UniqueUtil.shortUUID().length());
    }

}
