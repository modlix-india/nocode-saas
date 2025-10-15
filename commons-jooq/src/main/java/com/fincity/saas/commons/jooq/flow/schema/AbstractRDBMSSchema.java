package com.fincity.saas.commons.jooq.flow.schema;

import com.fincity.nocode.kirun.engine.json.schema.Schema;

public abstract class AbstractRDBMSSchema extends Schema {

    protected AbstractRDBMSSchema() {
        super();
    }

    protected AbstractRDBMSSchema(Schema schema) {
        super(schema);
    }
}
