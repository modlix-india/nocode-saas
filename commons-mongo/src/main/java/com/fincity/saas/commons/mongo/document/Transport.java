package com.fincity.saas.commons.mongo.document;

import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.model.TransportObject;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'clientCode': 1}", name = "transportFilteringIndex")
// Transport documents carry the whole encoded app, so they are around 1MB each: ui.transport is
// 231MB across 218 documents on production. That makes a collection scan here cost far more than
// the document count suggests -- a lookup by unique code was reading the entire 231MB and taking
// up to 380ms. Both of these shapes appear in mongod's slow-op log.
@CompoundIndex(def = "{'uniqueTransportCode': 1}", name = "transportUniqueCodeIndex")
@CompoundIndex(def = "{'createdAt': 1}", name = "transportCreatedAtIndex")
@Accessors(chain = true)
public class Transport extends AbstractOverridableDTO<Transport> {

    private static final long serialVersionUID = -5436810186809455453L;

    private String uniqueTransportCode;
    private List<TransportObject> objects;
    private String type;
    private String encodedModl;

    @Override
    public Mono<Transport> applyOverride(Transport base) {
        return Mono.just(this);
    }

    @Override
    public Mono<Transport> extractDifference(Transport base) {
        return Mono.just(this);
    }
}
