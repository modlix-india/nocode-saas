package com.fincity.saas.notification.document;

import java.io.Serial;
import java.util.Map;

import com.fincity.saas.commons.jooq.enums.ConnectionSubType;
import com.fincity.saas.commons.jooq.enums.ConnectionType;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
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
