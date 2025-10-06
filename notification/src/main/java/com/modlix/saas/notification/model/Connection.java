package com.modlix.saas.notification.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Connection implements Serializable {

    @Serial
    private static final long serialVersionUID = 444073774241445945L;

    private String connectionType;
    private String connectionSubType;
    private Map<String, Object> connectionDetails;
    private Boolean isAppLevel = Boolean.FALSE;
    private Boolean onlyThruKIRun = Boolean.FALSE;
}
