/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.commons.core.jooq;


import com.fincity.saas.commons.core.jooq.tables.CoreTokens;
import com.fincity.saas.commons.core.jooq.tables.records.CoreTokensRecord;

import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling foreign key relationships and constraints of tables in
 * core.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<CoreTokensRecord> KEY_CORE_TOKENS_PRIMARY = Internal.createUniqueKey(CoreTokens.CORE_TOKENS, DSL.name("KEY_core_tokens_PRIMARY"), new TableField[] { CoreTokens.CORE_TOKENS.ID }, true);
}
