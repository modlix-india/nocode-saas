package com.modlix.saas.commons2.mongo.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.modlix.saas.commons2.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'objectAppCode': 1, 'objectName': 1, 'clientCode': 1}", name = "versionFilteringIndex")
@Accessors(chain = true)
public class Version extends AbstractDTO<String, String> {

    private static final long serialVersionUID = -4689349735902306562L;

    private String objectName;
    private String objectAppCode;
    private String clientCode;
    private String message;

    private String objectType;

    private Map<String, Object> object; // NOSONAR
    private int versionNumber = 1;
}

