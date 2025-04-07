package com.fincity.saas.commons.core.dto;

import com.google.gson.JsonElement;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.util.MultiValueMap;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@Accessors(chain = true)
public class RestRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5293001147710257733L;

    private String url;
    private MultiValueMap<String, String> headers;
    private Map<String, String> pathParameters;
    private Map<String, String> queryParameters;
    private int timeout = 300;
    private String method;
    private JsonElement payload;
    private boolean ignoreDefaultHeaders = false;
}
