package com.fincity.saas.commons.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DataObject implements Serializable {
    @Serial
    private static final long serialVersionUID = 2698669653996010003L;

    private String message;
    private Map<String, Object> data; // NOSONAR
}
