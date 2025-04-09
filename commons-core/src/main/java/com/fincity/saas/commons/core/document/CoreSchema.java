package com.fincity.saas.commons.core.document;

import com.fincity.saas.commons.mongo.document.AbstractSchema;
import java.io.Serial;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "schema")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "coreSchemaFilteringIndex")
public class CoreSchema extends AbstractSchema<CoreSchema> {

    @Serial
    private static final long serialVersionUID = -3965005226382696687L;

    public CoreSchema() {}

    public CoreSchema(CoreSchema schema) {
        super(schema);
    }
}
