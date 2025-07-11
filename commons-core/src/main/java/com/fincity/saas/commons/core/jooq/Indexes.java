/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.commons.core.jooq;


import com.fincity.saas.commons.core.jooq.tables.CoreTokens;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling indexes of tables in core.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index CORE_TOKENS_K1_USER_CLIENT_APP_CODE_CONNECTION = Internal.createIndex(DSL.name("K1_USER_CLIENT_APP_CODE_CONNECTION"), CoreTokens.CORE_TOKENS, new OrderField[] { CoreTokens.CORE_TOKENS.USER_ID, CoreTokens.CORE_TOKENS.CLIENT_CODE, CoreTokens.CORE_TOKENS.APP_CODE, CoreTokens.CORE_TOKENS.CONNECTION_NAME }, false);
    public static final Index CORE_TOKENS_K2_CLIENT_APP_CONNECTION = Internal.createIndex(DSL.name("K2_CLIENT_APP_CONNECTION"), CoreTokens.CORE_TOKENS, new OrderField[] { CoreTokens.CORE_TOKENS.CLIENT_CODE, CoreTokens.CORE_TOKENS.APP_CODE, CoreTokens.CORE_TOKENS.CONNECTION_NAME }, false);
}
