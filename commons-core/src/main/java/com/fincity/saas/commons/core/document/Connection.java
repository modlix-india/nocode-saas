package com.fincity.saas.commons.core.document;

import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "connectionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Connection extends AbstractOverridableDTO<Connection> {

    @Serial
    private static final long serialVersionUID = -5507743337705010640L;

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
