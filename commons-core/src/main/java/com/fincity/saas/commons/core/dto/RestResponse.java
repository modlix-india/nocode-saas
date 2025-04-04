package com.fincity.saas.commons.core.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RestResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 8872204643847233412L;

    private Object data;
    private Map<String, String> headers;
    private int status;
}
