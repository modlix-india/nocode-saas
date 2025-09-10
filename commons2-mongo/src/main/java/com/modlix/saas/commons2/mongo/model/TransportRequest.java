package com.modlix.saas.commons2.mongo.model;

import com.modlix.saas.commons2.mongo.enums.TransportFileType;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TransportRequest implements Serializable {

    private static final long serialVersionUID = -5427188887375118053L;

    private String clientCode;
    private String appCode;
    private String name;
    private Map<String, List<String>> objectList;
    private TransportFileType fileType = TransportFileType.ZIP;
}

