package com.fincity.saas.commons.core.document;

import com.fincity.saas.commons.mongo.document.AbstractFunction;
import java.io.Serial;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "function")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "coreFunctionFilteringIndex")
public class CoreFunction extends AbstractFunction<CoreFunction> {

    @Serial
    private static final long serialVersionUID = 229558978137767412L;

    public CoreFunction() {}

    public CoreFunction(CoreFunction function) {
        super(function);
    }
}
