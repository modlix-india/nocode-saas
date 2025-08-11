package com.fincity.saas.ui.document;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'objectAppCode': 1, 'objectName': 1, 'clientCode': 1}", name = "mobileAppGenerationStatusFilteringIndex")
@Accessors(chain = true)
public class MobileAppGenerationStatus extends AbstractUpdatableDTO<String, String> {

    @Serial
    private static final long serialVersionUID = -2421283179190414360L;

    private String appCode;
    private String clientCode;
    private String mobileAppKey;
    private Status status;
    private int version = 0;
    private String errorMessage;

    public static enum Status {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILED;
    }
}