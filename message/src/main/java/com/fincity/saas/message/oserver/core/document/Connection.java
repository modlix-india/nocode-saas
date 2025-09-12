package com.fincity.saas.message.oserver.core.document;

import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import java.io.Serial;
import java.util.Map;
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
    private static final long serialVersionUID = 444073774241445945L;

    private ConnectionType connectionType;
    private ConnectionSubType connectionSubType;
    private Map<String, Object> connectionDetails;
    private Boolean isAppLevel = Boolean.FALSE;
    private Boolean onlyThruKIRun = Boolean.FALSE;

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
