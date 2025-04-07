package com.fincity.saas.commons.core.document;

import com.fincity.saas.commons.mongo.document.AbstractFiller;
import java.io.Serial;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "filler")
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "coreFillerFilteringIndex")
public class CoreFiller extends AbstractFiller<CoreFiller> {
    @Serial
    private static final long serialVersionUID = -3965005226382696687L;

    public CoreFiller() {}

    public CoreFiller(CoreFiller filler) {
        super(filler);
    }
}
