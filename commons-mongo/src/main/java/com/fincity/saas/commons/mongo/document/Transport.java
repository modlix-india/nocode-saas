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
	public Mono<Transport> makeOverride(Transport base) {
		return Mono.just(this);
	}
}
