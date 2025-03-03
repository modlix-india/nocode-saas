package com.fincity.saas.core.document;

import java.io.Serial;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "connectionFilteringIndex")
@CompoundIndex(def = "{'appCode': 1, 'clientCode': 1, 'connectionType': 1}", name = "connectionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Connection extends AbstractOverridableDTO<Connection> {

	@Serial
	private static final long serialVersionUID = 6867667971064931222L;

	private ConnectionType connectionType;
	private ConnectionSubType connectionSubType;
	private Map<String, Object> connectionDetails; // NOSONAR
	private Boolean isAppLevel = false;
	private Boolean onlyThruKIRun = false;

	public Connection(Connection base) {
		super(base);
		this.connectionType = base.connectionType;
		this.connectionSubType = base.connectionSubType;
		this.connectionDetails = CloneUtil.cloneMapObject(base.connectionDetails);
		this.isAppLevel = base.isAppLevel;
		this.onlyThruKIRun = base.onlyThruKIRun;
	}

	@Override
	public Mono<Connection> applyOverride(Connection base) {
		return Mono.just(this);
	}

	@Override
	public Mono<Connection> makeOverride(Connection base) {
		return Mono.just(this);
	}
}
