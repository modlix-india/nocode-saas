package com.fincity.saas.message.model.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseMessageRequest implements Serializable {

    private ULong userId;
    private String connectionName;

    public boolean hasConnection() {
        return connectionName != null && !connectionName.isEmpty();
    }
}
